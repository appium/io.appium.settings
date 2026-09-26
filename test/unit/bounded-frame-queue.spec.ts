import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {BoundedFrameQueue} from '../../lib/utils/index.js';

describe('BoundedFrameQueue', function () {
  it('should drop the oldest item once capacity is exceeded', async function () {
    const queue = new BoundedFrameQueue<number>(3);
    for (let i = 0; i < 5; i++) {
      queue.push(i);
    }

    const items: number[] = [];
    for await (const item of queue) {
      items.push(item);
      if (items.length >= 3) {
        break;
      }
    }
    assert.deepStrictEqual(items, [2, 3, 4]);
  });

  it('should protect items matched by isProtected from eviction', async function () {
    interface Item {
      id: number;
      isConfig: boolean;
    }
    // Reproduces the reported regression: a config frame pushed first must still be
    // present after 60 subsequent ordinary frames overflow a capacity-5 queue.
    const queue = new BoundedFrameQueue<Item>(5, (item) => item.isConfig);

    queue.push({id: 0, isConfig: true});
    for (let i = 1; i <= 60; i++) {
      queue.push({id: i, isConfig: false});
    }

    const items: Item[] = [];
    for await (const item of queue) {
      items.push(item);
      if (items.length >= 5) {
        break;
      }
    }

    assert.ok(
      items.some((item) => item.id === 0 && item.isConfig),
      'expected the protected config item to survive eviction',
    );
  });

  it('should fall back to dropping the true oldest item once every buffered item is protected', async function () {
    const queue = new BoundedFrameQueue<number>(2, () => true);
    queue.push(1);
    queue.push(2);
    queue.push(3);

    const items: number[] = [];
    for await (const item of queue) {
      items.push(item);
      if (items.length >= 2) {
        break;
      }
    }
    assert.deepStrictEqual(items, [2, 3]);
  });
});
