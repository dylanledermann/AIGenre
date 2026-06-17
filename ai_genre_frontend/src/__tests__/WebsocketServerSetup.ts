import {setupServer} from 'msw/node';
import config from '../config';
import { ws } from 'msw';

// Default urls to use
export const BASE_URL = config.api.baseUrl;
export const WS_URL = `${config.api.protocol}://${window.location.host}${config.api.websocketBaseUrl}`;

// setup server for testing
export const server = setupServer();
beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// Build stomp frame
export const stompFrame = (
    command: string,
    headers: Record<string, string> = {},
    body = ''
) => {
    // Combine headers to a map, then join with \n
    const headerLines = Object.entries(headers)
        .map(([k, v]) => `${k}:${v}`)
        .join('\n');
    // Create message as string -> command headerLines body all separatable
    return `${command}\n${headerLines}\n\n${body}\x00`;
};

// Parse the stomp frame into its sections (command, headers, body)
export const parseStompFrame = (raw: string) => {
    const [headerPart, ...bodyParts] = raw.split('\n\n');
    const [command, ...headerLines] = headerPart.split('\n');
    const headers = Object.fromEntries(
        headerLines
            .filter(Boolean)
            .map(line => {
                const [key, ...rest] = line.split(':');
                return [key, rest.join(':')]
            })
    );

    return {command: command.trim(), headers, body: bodyParts.join('\n\n').replace('\x00', '')};
};

type MessageHandler = (params: {
    destination: string;
    subscriptionId: string;
    send: (payload: unknown) => void
}) => void;

export const createStompServer = (url: string, onSubscribe: MessageHandler) => {
    return ws.link(url).addEventListener('connection', ({ client }) => {
        client.addEventListener('message', (event) => {
        const frame = parseStompFrame(event.data as string);

        if (frame.command === 'CONNECT') {
            client.send(stompFrame('CONNECTED', { version: '1.2', 'heart-beat': '0,0' }));
        }

        if (frame.command === 'SUBSCRIBE') {
            const destination = frame.headers['destination'];
            const subscriptionId = frame.headers['id'];

            // Provide a typed send helper so tests never touch raw frames
            const send = (payload: unknown) => {
            client.send(stompFrame(
                'MESSAGE',
                { destination, subscription: subscriptionId, 'message-id': '1' },
                JSON.stringify(payload)
            ));
            };

            onSubscribe({ destination, subscriptionId, send });
        }
        });
    });
};

// Throws error upon connecting to stomp server
export const createAndErrorServer = (url: string) => {
    return ws.link(url).addEventListener('connection', ({ client }) => {
        client.addEventListener('message', (event) => {
        const frame = parseStompFrame(event.data as string);

        if (frame.command === 'CONNECT') {
            client.send(stompFrame('CONNECTED', { version: '1.2', 'heart-beat': '0,0' }));
        }

        if (frame.command === 'SUBSCRIBE') {
            client.send(stompFrame('ERROR', { message: 'Subscription failed' }, 'Subscription failed'));
            setTimeout(() => client.close(), 0);
        }
        });
    });
};

// Immediately closes the stomp server after subscription
export const createAndCloseServer = (url: string) => {
    return ws.link(url).addEventListener('connection', ({ client }) => {
        client.addEventListener('message', (event) => {
        const frame = parseStompFrame(event.data as string);

        if (frame.command === 'CONNECT') {
            client.send(stompFrame('CONNECTED', { version: '1.2', 'heart-beat': '0,0' }));
        }

        if (frame.command === 'SUBSCRIBE') {
            setTimeout(() => client.close(), 0);
        }
        });
    });
};
