import { useCallback, useEffect, useRef, useState } from 'react';
import {
  WebsocketStatuses,
  type WebsocketData,
  type WebsocketState,
} from '../../types/WebsocketTypes/WebsocketTypes';
import { WebsocketContext } from './WebsocketContext';

const defaultState = (): WebsocketState => ({
  status: WebsocketStatuses.CONNECTING,
  results: null,
  error: null,
});

const WebsocketProvider = ({ children }: { children: React.ReactNode }) => {
  // Tracks taskId to websocket
  const websockets = useRef<Map<string, WebSocket>>(new Map());
  // Array of calls made and the response
  const [calls, setCalls] = useState<string[]>([]);
  // Tracks taskid to websocket state
  const [connections, setConnections] = useState<Map<string, WebsocketState>>(new Map());

  const updateState = useCallback((taskId: string, patch: Partial<WebsocketState>) => {
    // Update state in connections
    setConnections((prev) => {
      const next = new Map(prev);
      const current = next.get(taskId) ?? defaultState();
      next.set(taskId, { ...current, ...patch });
      return next;
    });
  }, []);

  const add = useCallback((data: WebsocketData) => {
    setCalls((prev) => {
      const next = structuredClone(prev);
      next.push(data.taskId);
      return next;
    });
  }, []);

  const open = useCallback(
    (taskId: string, url: string) => {
      const exists = websockets.current.has(taskId);

      if (!exists) {
        const newWebsocket = new WebSocket(url);

        // Update connections and websockets
        websockets.current.set(taskId, newWebsocket);

        setConnections((prev) => {
          const next = new Map(prev);
          next.set(taskId, defaultState());
          return next;
        });

        // Set up new websocket
        newWebsocket.onopen = () => {
          updateState(taskId, { status: WebsocketStatuses.PENDING });
        };

        newWebsocket.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data as string) as WebsocketData;
            console.log(message);
            updateState(taskId, { message } as Partial<WebsocketState>);
            if (
              message.status == WebsocketStatuses.COMPLETE ||
              message.status == WebsocketStatuses.FAILED
            )
              newWebsocket.close();
          } catch {
            updateState(taskId, {
              status: WebsocketStatuses.FAILED,
              error: 'Failed to parse message',
            });
          }
        };

        newWebsocket.onerror = () => {
          updateState(taskId, { status: WebsocketStatuses.FAILED, error: 'Websocket error' });
        };

        newWebsocket.onclose = () => {};
      }

      // Add new call to calls
      setCalls((prev) => {
        const next = structuredClone(prev);
        next.push(taskId);
        return next;
      });
    },
    [updateState],
  );

  const close = useCallback((taskId: string) => {
    websockets.current.get(taskId)?.close();
  }, []);

  const closeAll = useCallback(() => {
    websockets.current.forEach((websocket) => websocket.close());
  }, []);

  useEffect(() => {
    // Do nothing on mount, but make sure all websockets are closed when unmounted
    return () => {
      closeAll();
    };
  }, [closeAll]);

  return (
    <WebsocketContext.Provider
      value={{
        connections,
        calls,
        add,
        open,
        close,
        closeAll,
      }}
    >
      {children}
    </WebsocketContext.Provider>
  );
};

export default WebsocketProvider;
