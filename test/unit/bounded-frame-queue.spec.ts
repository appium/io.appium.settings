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

  describe('dependencyChain', function () {
    interface Item {
      id: string;
      track: 'video' | 'audio';
      isConfig?: boolean;
      isKeyFrame?: boolean;
    }
    const isChainMember = (item: Item) => item.track === 'video' && !item.isConfig;
    const isSyncPoint = (item: Item) => Boolean(item.isKeyFrame);

    it('should discard dependent frames left behind by an evicted keyframe when no later keyframe exists', async function () {
      // Reproduces the review comment's exact repro: config + one keyframe + 59 dependent
      // frames overflow a capacity-60 queue by one, evicting the keyframe - every dependent
      // frame after it is now undecodable and must go too, since no later keyframe exists.
      const queue = new BoundedFrameQueue<Item>(60, (item) => Boolean(item.isConfig), {isChainMember, isSyncPoint});

      queue.push({id: 'config', track: 'video', isConfig: true});
      queue.push({id: 'key', track: 'video', isKeyFrame: true});
      for (let i = 0; i < 59; i++) {
        queue.push({id: `dep${i}`, track: 'video'});
      }
      queue.push({id: 'sentinel', track: 'audio'});

      const items: Item[] = [];
      for await (const item of queue) {
        items.push(item);
        if (item.id === 'sentinel') {
          break;
        }
      }

      assert.deepStrictEqual(
        items.map((item) => item.id),
        ['config', 'sentinel'],
        'expected every video frame after the evicted keyframe to be discarded, leaving only config',
      );
    });

    it('should stop the cascade at the next keyframe, leaving later items (including interleaved audio) intact', async function () {
      const queue = new BoundedFrameQueue<Item>(5, (item) => Boolean(item.isConfig), {isChainMember, isSyncPoint});

      queue.push({id: 'config', track: 'video', isConfig: true});
      queue.push({id: 'key1', track: 'video', isKeyFrame: true});
      queue.push({id: 'dep1', track: 'video'});
      queue.push({id: 'audio1', track: 'audio'});
      queue.push({id: 'key2', track: 'video', isKeyFrame: true});
      // Overflows capacity 5 by one, evicting key1: dep1 (which depended on it) cascades
      // away too, audio1 is skipped over untouched, and key2 stops the cascade since it
      // doesn't depend on anything before it.
      queue.push({id: 'dep2', track: 'video'});

      const items: Item[] = [];
      for await (const item of queue) {
        items.push(item);
        if (items.length >= 4) {
          break;
        }
      }

      assert.deepStrictEqual(
        items.map((item) => item.id),
        ['config', 'audio1', 'key2', 'dep2'],
      );
    });

    it('should keep discarding new dependent frames until a fresh keyframe arrives', async function () {
      const queue = new BoundedFrameQueue<Item>(3, (item) => Boolean(item.isConfig), {isChainMember, isSyncPoint});

      queue.push({id: 'config', track: 'video', isConfig: true});
      queue.push({id: 'key1', track: 'video', isKeyFrame: true});
      queue.push({id: 'dep1', track: 'video'});
      // Overflows capacity 3, evicting key1; the cascade finds no later keyframe (queue
      // ends at dep1), so dep1 is also dropped and the queue now awaits a fresh keyframe.
      queue.push({id: 'dep2', track: 'video'});

      // Pushed while awaiting a fresh keyframe: silently discarded, not merely evicted
      // later - it must never reach the consumer even though there is room to buffer it.
      queue.push({id: 'dep3', track: 'video'});
      // Audio has no dependency chain, so it bypasses the gate entirely.
      queue.push({id: 'audio1', track: 'audio'});
      // The first keyframe pushed since the gap clears the gate and resumes buffering.
      queue.push({id: 'key2', track: 'video', isKeyFrame: true});

      const items: Item[] = [];
      for await (const item of queue) {
        items.push(item);
        if (items.length >= 3) {
          break;
        }
      }

      assert.deepStrictEqual(
        items.map((item) => item.id),
        ['config', 'audio1', 'key2'],
      );
    });

    it('should suppress dependent frames after an externally-reported gap, even without any local eviction', async function () {
      // Reproduces upstream loss (e.g. the on-device queue dropping a keyframe under
      // backpressure before it ever reaches this queue) rather than local eviction -
      // the queue never overflows here, so nothing would trigger evictOnce() on its own.
      const queue = new BoundedFrameQueue<Item>(60, (item) => Boolean(item.isConfig), {isChainMember, isSyncPoint});

      queue.push({id: 'config', track: 'video', isConfig: true});
      // The producer detected a sequence gap (a keyframe was dropped upstream) before
      // pushing the next frame it actually received.
      queue.notifyGap();
      queue.push({id: 'dep', track: 'video'});
      queue.push({id: 'audio1', track: 'audio'});
      queue.push({id: 'key', track: 'video', isKeyFrame: true});

      const items: Item[] = [];
      for await (const item of queue) {
        items.push(item);
        if (items.length >= 3) {
          break;
        }
      }

      assert.deepStrictEqual(
        items.map((item) => item.id),
        ['config', 'audio1', 'key'],
        'expected the dependent frame after the gap to be discarded, resuming only at the next keyframe',
      );
    });
  });
});
