import {setupServer} from 'msw/node';
import config from '../config';

// Default urls to use
export const BASE_URL = config.api.baseUrl;
export const WS_URL = config.api.websocketBaseUrl;

// setup server for testing
export const server = setupServer();
beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());