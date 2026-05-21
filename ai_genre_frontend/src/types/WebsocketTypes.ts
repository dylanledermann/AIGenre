// All possible websocket statuses to be converted to a type
export const WebsocketStatuses = {
  CONNECTING: {
    value: 0,
    name: 'CONNECTING',
  } as const,
  PENDING: {
    value: 1,
    name: 'PENDING',
  } as const,
  PROCESSING: {
    value: 2,
    name: 'PROCESSING',
  } as const,
  COMPLETE: {
    value: 3,
    name: 'COMPLETE',
  } as const,
  FAILED: {
    value: 4,
    name: 'FAILED',
  } as const,
} as const;

// Enum-adjacent websocket type. Works similar to python enums
export type WebsocketStatus = (typeof WebsocketStatuses)[keyof typeof WebsocketStatuses];

export interface Result {
  accuracy: string;
  genre: string;
}

export interface WebsocketState {
  status: WebsocketStatus;
  results: Result | null;
  error: string | null;
}

export interface WebsocketData extends WebsocketState {
  taskId: string;
}

// Default values to be used for testing
// const defaultPendingState = (): WebsocketState => ({
//   status: WebsocketStatuses.PENDING,
//   results: null,
//   error: null,
// });

// const defaultProcessingState = (): WebsocketState => ({
//   status: WebsocketStatuses.PROCESSING,
//   results: null,
//   error: null,
// });

// const defaultCompleteState = (): WebsocketState => ({
//   status: WebsocketStatuses.COMPLETE,
//   results: {
//     accuracy: "83.001%",
//     genre: "Pop",
//   },
//   error: null,
// });

// const defaultErrorState = (): WebsocketState => ({
//   status: WebsocketStatuses.FAILED,
//   results: null,
//   error: "Some Error Occurred"
// });

// setConnections(() => {
//   const next = new Map();
//   next.set("2b2dc940-359b-4684-b8ff-7528143b2a78", defaultState());
//   next.set("973febdb-5c79-4999-83f0-6423eb5111b0", defaultProcessingState());
//   next.set("56fb7369-422e-4166-ac55-682c9000dc5b", defaultPendingState());
//   next.set("d5d6ec6a-5b18-4a79-93b7-aa9a66b4881a", defaultCompleteState());
//   next.set("ca993858-9f4b-401d-8303-a399e5bb4bae", defaultErrorState());

//   return next;
// });
// setCalls(() => {
//   const next = [];
//   next.push(
//     "2b2dc940-359b-4684-b8ff-7528143b2a78",
//     "56fb7369-422e-4166-ac55-682c9000dc5b",
//     "973febdb-5c79-4999-83f0-6423eb5111b0",
//     "d5d6ec6a-5b18-4a79-93b7-aa9a66b4881a",
//     "ca993858-9f4b-401d-8303-a399e5bb4bae"
//   );

//   return next;
// });
