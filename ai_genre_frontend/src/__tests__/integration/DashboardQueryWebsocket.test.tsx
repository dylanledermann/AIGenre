import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {BASE_URL, createStompServer, server, WS_URL} from '../WebsocketServerSetup';
import WebsocketProvider from '../../hooks/WebsocketHook/WebsocketProvider/WebsocketProvider';
import { render, waitFor, screen } from '@testing-library/react';
import Dashboard from '../../pages/Dashboard';
import { http, HttpResponse, ws } from 'msw';
import { WebsocketStatuses } from '../../types/WebsocketTypes/WebsocketTypes';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import config from '../../config';

// Query client for ReactQuery
const makeQueryClient = () => 
    new QueryClient({
        defaultOptions: {
            queries: {
                retry: false,
                staleTime: 0
            },
        },
    });

// Wrapper with dashboard
const renderDashboard = (queryClient: QueryClient) => 
    render(
        <QueryClientProvider client={queryClient}>
            <WebsocketProvider>
                <Dashboard />
            </WebsocketProvider>
        </QueryClientProvider>
    );

const makeFile = (name: string, type = 'audio/wav', size = 1024): File => {
  const buffer = new Uint8Array(size);
  return new File([buffer], name, { type });
};

beforeEach(() => {
    server.use(
        ws.link(`${WS_URL}/*`).addEventListener('connection', () => {})
    );
});

describe('DashboardQueryWebsocket', () => {
    describe('successful file upload', () => {
        it('creates websocket on successful upload', async () => {
            // Initialize the components (query client for react query, render the dashboard)
            const queryClient = makeQueryClient();
            renderDashboard(queryClient);

            // default values to be used
            const taskId = 'task-1';

            // spy on fetch and websocket
            const fetchSpy = vi.fn();
            const wsSpy = vi.fn();
            server.use(
                http.post(`${BASE_URL}/query`, () => {
                    fetchSpy();
                    return HttpResponse.json({
                        taskId: taskId,
                        status: WebsocketStatuses.PENDING.name,
                        results: null,
                        error: null,
                    });
                }),
                createStompServer(WS_URL, () => {
                   wsSpy(); 
                }),
            );

            // File to be uploaded and input element
            const input = document.getElementById('browse');
            const file = makeFile('audio.wav');
            
            await userEvent.upload(input!, file);

            // Click the upload button to submit
            const uploadBtn = document.getElementById('upload');

            await userEvent.click(uploadBtn!);

            // Websocket should be initialize and state should be set
            await waitFor(() => {
                    expect(fetchSpy).toHaveBeenCalledOnce();
                    expect(wsSpy).toHaveBeenCalledOnce();
                    expect(screen.getAllByText(WebsocketStatuses.PENDING.name).length).toBe(2);
                }
            );
        });

        it('adds instead of opens websocket when response status is complete/failed', async () => {
            // Initialize the components (query client for react query, render the dashboard)
            const queryClient = makeQueryClient();
            renderDashboard(queryClient);

            // default values to be used
            const taskId = 'task-1';
            const url = `${config.api.websocketBaseUrl}/topic/results/${taskId}`;
            console.log(url);

            // spy on fetch
            const fetchSpy = vi.fn();
            const wsSpy = vi.fn();
            server.use(
                http.post(`${BASE_URL}/query`, () => {
                    fetchSpy();
                    return HttpResponse.json({
                        taskId: taskId,
                        status: WebsocketStatuses.COMPLETE.name,
                        results: {
                            genre: 'GENRE', 
                            accuracy: 'ACC'
                        },
                        error: null,
                    });
                }),
                createStompServer(WS_URL, () => {
                    wsSpy();
                }),
            );

            // File to be uploaded and input element
            const input = document.getElementById('browse');
            const file = makeFile('audio.wav');
            
            await userEvent.upload(input!, file);

            // Click the upload button to submit
            const uploadBtn = document.getElementById('upload');

            await userEvent.click(uploadBtn!);

            // Websocket should not be initialized (wsSpy not called), but state should be updated
            await waitFor(() => {
                    expect(fetchSpy).toHaveBeenCalledOnce();
                    expect(wsSpy).not.toHaveBeenCalled();
                    expect(screen.getAllByText(WebsocketStatuses.COMPLETE.name).length).toBe(2);
                }
            );
        });

        it('updates on screen value on websocket status change', async () => {
            // Initialize the components (query client for react query, render the dashboard)
            const queryClient = makeQueryClient();
            renderDashboard(queryClient);

            // default values to be used
            const taskId = 'task-1';
            const url = `${config.api.websocketBaseUrl}/topic/results/${taskId}`;
            const completeMessage = {status: 'COMPLETE', results: {genre: 'GENRE', accuracy: 'ACC'}};
            console.log(url);

            // spy on fetch
            const fetchSpy = vi.fn();
            const wsSpy = vi.fn();
            server.use(
                http.post(`${BASE_URL}/query`, () => {
                    fetchSpy();
                    return HttpResponse.json({
                        taskId: taskId,
                        status: WebsocketStatuses.PENDING.name,
                        results: null,
                        error: null,
                    });
                }),
                createStompServer(WS_URL, ({send}) => {
                    wsSpy();
                    send({
                        taskId: taskId,
                        ...completeMessage
                    });
                }),
            );

            // File to be uploaded and input element
            const input = document.getElementById('browse');
            const file = makeFile('audio.wav');
            
            await userEvent.upload(input!, file);

            // Click the upload button to submit
            const uploadBtn = document.getElementById('upload');

            await userEvent.click(uploadBtn!);

            // Websocket should be initialize and state should be set
            await waitFor(() => {
                    expect(fetchSpy).toHaveBeenCalledOnce();
                    expect(wsSpy).toHaveBeenCalledOnce();
                    expect(screen.getAllByText(WebsocketStatuses.COMPLETE.name).length).toBe(2);
                }
            );
        });
    });
});