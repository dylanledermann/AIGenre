import {ws} from 'msw';
import {setupServer} from 'msw/node';
import { useWebsockets } from '../WebsocketContext';
import { act, renderHook, waitFor } from '@testing-library/react';
import WebsocketProvider from './WebsocketProvider';
import '@testing-library/jest-dom';
import { type ReactNode } from 'react';
import { WebsocketStatuses } from '../../../types/WebsocketTypes/WebsocketTypes';

const WS_URL = 'wss://api.example.com';

const server = setupServer();
beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const wrapper = ({children}: {children: ReactNode}) => (
    <WebsocketProvider>{children}</WebsocketProvider>
);

const setup = () => renderHook(() => useWebsockets(), {wrapper});

describe('WebsocketProvider', () => {
    describe('testing provider functions', () => {
        it('opens websocket', async () => {
            // Create task id and server config for websocket listening
            const taskId = 'task-1';
            const url = `${WS_URL}/${taskId}`;
            server.use(
                ws.link(url).addEventListener(
                    'connection',
                    () => {},
                ),
            );

            // act() ensures the setup runs after the websocket setup is complete
            const {result} = setup();

            act(() => {
                result.current.open(taskId, url);
            });

            // Initial state should be connecting until the websocket is opened
            expect(result.current.connections.has(taskId)).toBe(true);
            expect(result.current.connections.get(taskId)!.status).toBe(WebsocketStatuses.CONNECTING);
            expect(result.current.calls[0]).toBe(taskId);
            
            // Verify websocket it opened and has the correct status
            await waitFor(() => {
                expect(result.current.connections.get(taskId)!.status).toBe(WebsocketStatuses.PENDING);
            });
        });

        it('updates values on websocket message', async () => {
            // Create task id and server config for websocket listening and message to be sent
            const taskId = 'task-1';
            const url = `${WS_URL}/${taskId}`;
            const completeMessage = {status: 'COMPLETE', results: {genre: 'GENRE', accuracy: 'ACC'}};
            server.use(
                ws.link(url).addEventListener(
                    'connection',
                    ({client}) => {
                        client.send(JSON.stringify(completeMessage));
                    },
                ),
            );

            // act() ensures the setup runs after the websocket setup is complete
            const {result} = setup();

            act(() => {
                result.current.open(taskId, url);
            });

            await waitFor(() => {
                // Websocket status should be updated to COMPLETE
                expect(result.current.connections.has(taskId)).toBe(true);
                expect(result.current.connections.get(taskId)!.status).toBe(WebsocketStatuses.COMPLETE);
                
                // calls should not be changed
                expect(result.current.calls[0]).toBe(taskId);
                expect(result.current.calls.length).toBe(1);
            });
        });

        it('handles invalid JSON messages', async () => {
            // Create task id and server config for websocket listening
            const taskId = 'task-1';
            const url = `${WS_URL}/${taskId}`;
            server.use(
                ws.link(url).addEventListener(
                    'connection',
                    ({client}) => {
                        client.send('INVALID JSON }{}{}{}}}}');
                    },
                ),
            );

            // act() ensures the setup runs after the websocket setup is complete
            const {result} = setup();

            act(() => {
                result.current.open(taskId, url);
            });

            await waitFor(() => {
                // Websocket status should be updated to FAILED with an error message
                expect(result.current.connections.has(taskId)).toBe(true);
                expect(result.current.connections.get(taskId)!.status).toBe(WebsocketStatuses.FAILED);
                expect(result.current.connections.get(taskId)!.error).toBe('Failed to parse message');
                
                // calls should not be changed
                expect(result.current.calls[0]).toBe(taskId);
                expect(result.current.calls.length).toBe(1);
            });
        });

        it('updates state on premature closing from the server', async () => {
            // Create task and websocket server
            const taskId = 'task-1';
            const url = `${WS_URL}/${taskId}`;
            server.use(
                ws.link(url).addEventListener(
                    'connection',
                    ({client}) => {
                        client.close();
                    },
                ),
            );

            // act() ensures the setup runs after the websocket setup is complete
            const {result} = setup();

            act(() => {
                result.current.open(taskId, url);
            });

            await waitFor(() => {
                // Websocket status should be updated to FAILED with an error message
                expect(result.current.connections.has(taskId)).toBe(true);
                expect(result.current.connections.get(taskId)!.status).toBe(WebsocketStatuses.FAILED);
                expect(result.current.connections.get(taskId)!.error).toBe('Connection closed unexpectedly');
                
                // calls should not be changed
                expect(result.current.calls[0]).toBe(taskId);
                expect(result.current.calls.length).toBe(1);
            });
        });

        it('does not re-add previous existing websockets', async () => {
            // Create task and websocket server
            const taskId = 'task-1';
            const url = `${WS_URL}/${taskId}`;
            const completeMessage = {status: 'COMPLETE', results: {genre: 'GENRE', accuracy: 'ACC'}};
            server.use(
                ws.link(url).addEventListener(
                    'connection',
                    ({client}) => {
                        client.send(JSON.stringify(completeMessage));
                    },
                ),
            );

            // act() ensures the setup runs after the websocket setup is complete
            const {result} = setup();

            act(() => {
                result.current.open(taskId, url);
            });

            // Wait for the complete message to be sent
            await waitFor(() => {
                // Websocket status should be updated to COMPLETE
                expect(result.current.connections.has(taskId)).toBe(true);
                expect(result.current.connections.get(taskId)!.status).toBe(WebsocketStatuses.COMPLETE);
                
                // calls should not be changed
                expect(result.current.calls[0]).toBe(taskId);
                expect(result.current.calls.length).toBe(1);
            });

            // re-open the same task id should just add the task id to calls
            act(() => {
                result.current.open(taskId, url);
            });

            // Need to wait for the setCalls state change
            await waitFor(() => {
               // Websocket status should be unchanged
                expect(result.current.connections.has(taskId)).toBe(true);
                expect(result.current.connections.get(taskId)!.status).toBe(WebsocketStatuses.COMPLETE);
                
                // a duplicate call should be added
                expect(result.current.calls.length).toBe(2);
                expect(result.current.calls[0]).toBe(result.current.calls[1]);
            });
        });
    });
});