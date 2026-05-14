export const config = {
  ap: {
    baseUrl: String(import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8000'),
    timeout: 10_000,
  },
  query: {
    staleTime: 300_000, // 5 minutes
    gcTime: 600_000, // 10 minutes
    retry: 1,
  },
} as const;
