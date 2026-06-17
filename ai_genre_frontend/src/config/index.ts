const config = {
  api: {
    baseUrl: String(import.meta.env.VITE_API_BASE_URL ?? '/api'),
    timeout: 10_000,
    websocketBaseUrl: String(import.meta.env.VITE_API_WEBSOCKET_BASE_URL ?? '/ws'),
    websocketTopic: '/topic/results',
    protocol: String(import.meta.env.VITE_API_WEBSOCKET_PROTOCOL ?? 'ws'),
  },
  query: {
    staleTime: 300_000, // 5 minutes
    gcTime: 600_000, // 10 minutes
    retry: 1,
  },
} as const;

export default config;
