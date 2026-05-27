import { createContext, useContext } from 'react';
import type { WebsocketData, WebsocketState } from '../../types/WebsocketTypes/WebsocketTypes';

export interface WebsocketContextValue {
  connections: Map<string, WebsocketState>;
  calls: string[];
  add: (data: WebsocketData) => void;
  open: (taskId: string, url: string) => void;
  close: (taskId: string, url: string) => void;
  closeAll: () => void;
}

export const WebsocketContext = createContext<WebsocketContextValue | null>(null);

export const useWebsockets = () => {
  const ctx = useContext(WebsocketContext);
  if (!ctx) throw new Error('useWebsockets must be used within a WebsocketProvider.');
  return ctx;
};
