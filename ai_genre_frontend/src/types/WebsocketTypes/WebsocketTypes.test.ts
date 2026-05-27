import { describe, expect, it } from 'vitest';
import { WebsocketStatuses } from './WebsocketTypes';

describe('WebsocketTypes', () => {
  describe('websocket statuses', () => {
    it('has correct numerical values', () => {
      // Check all the numbers are correct (incase of future changes)
      expect(WebsocketStatuses.CONNECTING.value).toBe(0);
      expect(WebsocketStatuses.PENDING.value).toBe(1);
      expect(WebsocketStatuses.PROCESSING.value).toBe(2);
      expect(WebsocketStatuses.COMPLETE.value).toBe(3);
      expect(WebsocketStatuses.FAILED.value).toBe(4);
    });

    it('has correct string names', () => {
      // Check all the names are correct (incase of future changes)
      expect(WebsocketStatuses.CONNECTING.name).toBe('CONNECTING');
      expect(WebsocketStatuses.PENDING.name).toBe('PENDING');
      expect(WebsocketStatuses.PROCESSING.name).toBe('PROCESSING');
      expect(WebsocketStatuses.COMPLETE.name).toBe('COMPLETE');
      expect(WebsocketStatuses.FAILED.name).toBe('FAILED');
    });
  });
});
