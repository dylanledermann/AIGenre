import config from '../config/index.ts';
import type { WebsocketData } from '../types/WebsocketTypes/WebsocketTypes.ts';

export const createQuery = async (file: File) => {
  const response: Response = await fetch(config.api.baseUrl + '/api/query', {
    method: 'POST',
    body: file,
  });
  if (!response.ok) throw new Error(`${response.status}`);
  return (await response.json()) as WebsocketData;
};
