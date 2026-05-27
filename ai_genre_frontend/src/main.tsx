import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import config from './config/index.ts';
import WebsocketProvider from './hooks/WebsocketHook/WebsocketProvider/WebsocketProvider.tsx';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { ...config.query },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <WebsocketProvider>
        <App />
      </WebsocketProvider>
    </QueryClientProvider>
  </StrictMode>,
);
