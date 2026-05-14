import { createContext, useCallback, useEffect, useRef, useState } from "react";
import type { WebsocketStatus } from "../types/WebsocketTypes";

interface WebsocketState<TResult = unknown> {
    status: WebsocketStatus;
    results: TResult[];
    error: string | null;
};

interface WebsocketData extends WebsocketState {
    taskId: string;
};

interface WebsocketContextValue {
    connections: Map<string, WebsocketState>;
    calls: WebsocketData[];
    open: (taskId: string, url: string) => void;
    close: (taskId: string, url: string) => void;
    closeAll: () => void;
};

const defaultState = (): WebsocketState => ({
    status: 'CONNECTING',
    results: [],
    error: null,
});

const WebsocketContext = createContext<WebsocketContextValue | null>(null);

const WebsocketProvider = ({children}: {children: React.ReactNode}) => {
    // Tracks taskId to websocket
    const websockets = useRef<Map<string, WebSocket>>(new Map());
    // Array of calls made and the response
    const [calls, setCalls] = useState<WebsocketData[]>([]);
    // Tracks taskid to websocket state
    const [connections, setConnections] = useState<Map<string, WebsocketState>>(new Map());

    const updateState = useCallback((taskId: string, patch: Partial<WebsocketState>) => {
        // Update all current array instances that are tracking the current taskId
        setCalls(prev => {
            const next = structuredClone(prev);
            next.map(current => {
                if (current.taskId == taskId) {
                    return {...current, ...patch} 
                }
                return current;
            });
            return next;
        });
        // Update state in connections
        setConnections(prev => {
            const next = new Map(prev);
            const current = next.get(taskId) ?? defaultState();
            next.set(taskId, {...current, ...patch});
            return next;
        });
    }, []);

    const open = useCallback((taskId: string, url: string) => {
        const exists = websockets.current.has(taskId);

        // Add new call to calls
        setCalls(prev => {
            const next = structuredClone(prev);
            next.push({taskId: taskId, ...connections.get(taskId)} as WebsocketData);
            return next;
        });

        if (!exists) {
            const newWebsocket = new WebSocket(url);

            // Update connections and websockets
            websockets.current.set(taskId, newWebsocket);
            
            setConnections(prev => {
                const next = new Map(prev);
                next.set(taskId, defaultState());
                return next;
            });

            // Set up new websocket
            newWebsocket.onopen = () => {
                updateState(taskId, {status: 'PENDING'});
            };

            newWebsocket.onmessage = (event) => {
                try {
                    const message = JSON.parse(event.data as string);
                    console.log(message);
                    updateState(taskId, {message} as Partial<WebsocketState>);
                } catch {
                    updateState(taskId, {status: 'FAILED', error: 'Failed to parse message'});
                }
            };

            newWebsocket.onerror = () => {
                updateState(taskId, {status: 'FAILED', error: 'Websocket error'});
            }

            newWebsocket.onclose = () => {};
        }
    }, []);

    const close = useCallback((taskId: string) => {
        websockets.current.get(taskId)?.close();
    }, []);

    const closeAll = useCallback(() => {
        websockets.current.forEach(websocket => websocket.close());
    }, []);

    useEffect(() => {
        // Do nothing on mount, but make sure all websockets are closed when unmounted
        return () => {
            closeAll();
        }
    }, []);

    return (
        <WebsocketContext.Provider value={{
            connections,
            calls,
            open,
            close,
            closeAll
        }}>
            {children}
        </WebsocketContext.Provider>
    );
};

export default WebsocketProvider;