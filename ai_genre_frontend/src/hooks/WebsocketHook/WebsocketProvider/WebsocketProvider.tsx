import { useCallback, useEffect, useRef, useState } from 'react';
import {
  WebsocketStatuses,
  type WebsocketData,
  type WebsocketState,
} from '../../../types/WebsocketTypes/WebsocketTypes';
import { WebsocketContext } from '../WebsocketContext';

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

  /**
   * This functions adds websocket information to calls and connections.
   * Used when a websocket should not be opened i.e. the task run is saved/cached on the backend.
   */
  const add = useCallback((data: WebsocketData) => {
    // Change status to WebsocketStatuses type, since this should be called with backend info (string)
    data.status = WebsocketStatuses[data.status as unknown as keyof typeof WebsocketStatuses];
    // Update state to have the information whether it is an update or adding the new info
    updateState(data.taskId, data);
    // Add the task id to the calls
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
            message.status = WebsocketStatuses[message.status as unknown as keyof typeof WebsocketStatuses];
            updateState(taskId, message as Partial<WebsocketState>);
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

        newWebsocket.onclose = () => {
          // onclose needs to use the current connections, not the state onopen
          setConnections(prev => {
            const current = prev.get(taskId);
            // Update saved state if task is not finished
            if (!current || current.status === WebsocketStatuses.COMPLETE || current.status === WebsocketStatuses.FAILED) {
              return prev;
            }

            const next = new Map(prev);
            next.set(taskId, {
              ...current,
              status: WebsocketStatuses.FAILED,
              error: 'Connection closed unexpectedly',
            });
            return next;
          });
          
          // Remove the task from the websockets ref
          websockets.current.delete(taskId);
        };
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
