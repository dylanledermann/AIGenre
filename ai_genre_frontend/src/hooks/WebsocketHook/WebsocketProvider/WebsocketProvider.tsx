import { useCallback, useEffect, useRef, useState } from 'react';
import {
  WebsocketStatuses,
  type WebsocketData,
  type WebsocketState,
} from '../../../types/WebsocketTypes/WebsocketTypes';
import { WebsocketContext } from '../WebsocketContext';
import { Client } from '@stomp/stompjs';
import config from '../../../config';

const defaultState = (): WebsocketState => ({
  status: WebsocketStatuses.CONNECTING,
  results: null,
  error: null,
});

const WebsocketProvider = ({ children }: { children: React.ReactNode }) => {
  // Tracks taskId to websocket
  const websockets = useRef<Map<string, Client>>(new Map());
  // Array of calls made and the response
  const [calls, setCalls] = useState<string[]>([]);
  // Tracks taskid to websocket state
  const [connections, setConnections] = useState<Map<string, WebsocketState>>(new Map());

  const updateState = useCallback((taskId: string, patch: Partial<WebsocketState>) => {
    // Update state in connections
    setConnections((prev) => {
      const next = new Map(prev);
      const current = next.get(taskId) ?? defaultState();
      console.log(current);
      // Need to reset error/message if different values
      // This is for the case where something fails/passes, then the patch changes
      // E.g. The socket disconnects (error value) then reconnects with completion (results value)
      if (patch.status != WebsocketStatuses.FAILED) {
        current.error = null;
      }
      if (patch.status != WebsocketStatuses.COMPLETE) {
        current.results = null;
      }
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
    (taskId: string, topic: string) => {
      const exists = websockets.current.has(taskId);

      if (!exists) {
        const client = new Client({
          webSocketFactory: () => new WebSocket(`${config.api.protocol}://${window.location.host}${config.api.websocketBaseUrl}`),

          onConnect: () => {
            // update state for the task to pending (connection was made)
            updateState(taskId, { status: WebsocketStatuses.PENDING });

            // Subscribe to the topic
            client.subscribe(topic, (event) => {
              try {
                // Parse the body and convert status to status type, then update state to the provided state
                const message = JSON.parse(event.body as string) as WebsocketData;
                message.status = WebsocketStatuses[message.status as unknown as keyof typeof WebsocketStatuses];
                updateState(taskId, message as Partial<WebsocketState>);
                if (
                  message.status == WebsocketStatuses.COMPLETE ||
                  message.status == WebsocketStatuses.FAILED
                )
                  // close the client if complete
                  client.deactivate();
              } catch {
                updateState(taskId, {
                  status: WebsocketStatuses.FAILED,
                  error: 'Failed to parse message',
                });
              }
            });
          },

          onStompError: () => {
            // Update the state to failed
            updateState(taskId, { status: WebsocketStatuses.FAILED, error: 'Websocket error' });
            // Close the websocket connection
            client.deactivate();
          },

          // Server side close
          onWebSocketClose: () => {
            console.log("DISCONNECTED");
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
          },

          // Client side close
          onDisconnect: () => {
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
          }
        });

        // Update connections and websockets
        websockets.current.set(taskId, client);
        setConnections((prev) => {
          const next = new Map(prev);
          next.set(taskId, defaultState());
          return next;
        });

        // Start websocket connection
        client.activate();
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
    websockets.current.get(taskId)?.deactivate();
  }, []);

  const closeAll = useCallback(() => {
    websockets.current.forEach((websocket) => websocket.deactivate());
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
