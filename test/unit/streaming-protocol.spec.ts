import assert from 'node:assert/strict';
import {describe, it} from 'node:test';

import {createParserState, parseFrames, StreamTrack, type StreamFrame} from '../../lib/commands/streaming-protocol.js';

function buildFrameBuffer(
  track: number,
  flags: number,
  sequence: bigint,
  timestampMicros: bigint,
  payload: Buffer,
): Buffer {
  const header = Buffer.alloc(28);
  header.write('APST', 0, 'ascii');
  header.writeUInt8(1, 4); // version
  header.writeUInt8(track, 5);
  header.writeUInt8(flags, 6);
  header.writeUInt8(0, 7); // reserved
  header.writeBigUInt64BE(sequence, 8);
  header.writeBigUInt64BE(timestampMicros, 16);
  header.writeUInt32BE(payload.length, 24);
  return Buffer.concat([header, payload]);
}

describe('streaming-protocol', function () {
  describe('parseFrames', function () {
    it('should parse a single well-formed frame', function () {
      const payload = Buffer.from([0xff, 0xd8, 0xaa, 0xbb, 0xff, 0xd9]);
      const buf = buildFrameBuffer(StreamTrack.Jpeg, 0, 42n, 123456n, payload);
      const state = createParserState();
      const frames = [...parseFrames(state, buf)];

      assert.strictEqual(frames.length, 1);
      assert.strictEqual(frames[0].track, StreamTrack.Jpeg);
      assert.strictEqual(frames[0].sequence, 42n);
      assert.strictEqual(frames[0].timestampMicros, 123456n);
      assert.deepStrictEqual(frames[0].data, payload);
      assert.strictEqual(frames[0].isConfig, false);
      assert.strictEqual(frames[0].isKeyFrame, false);
      assert.strictEqual(frames[0].isEndOfStream, false);
    });

    it('should decode all flag bits', function () {
      const payload = Buffer.from([1, 2, 3]);
      const buf = buildFrameBuffer(StreamTrack.Video, 0x01 | 0x02 | 0x04, 0n, 0n, payload);
      const state = createParserState();
      const [frame] = [...parseFrames(state, buf)];

      assert.strictEqual(frame.isConfig, true);
      assert.strictEqual(frame.isKeyFrame, true);
      assert.strictEqual(frame.isEndOfStream, true);
    });

    it('should handle a zero-length payload', function () {
      const buf = buildFrameBuffer(StreamTrack.Audio, 0x04, 1n, 1n, Buffer.alloc(0));
      const state = createParserState();
      const [frame] = [...parseFrames(state, buf)];

      assert.strictEqual(frame.data.length, 0);
      assert.strictEqual(frame.isEndOfStream, true);
    });

    it('should parse multiple frames delivered in a single chunk', function () {
      const buf1 = buildFrameBuffer(StreamTrack.Jpeg, 0, 0n, 0n, Buffer.from('one'));
      const buf2 = buildFrameBuffer(StreamTrack.Jpeg, 0, 1n, 100n, Buffer.from('two'));
      const state = createParserState();
      const frames = [...parseFrames(state, Buffer.concat([buf1, buf2]))];

      assert.strictEqual(frames.length, 2);
      assert.strictEqual(frames[0].data.toString(), 'one');
      assert.strictEqual(frames[1].data.toString(), 'two');
      assert.strictEqual(frames[1].sequence, 1n);
    });

    it('should reassemble a frame split across many small chunks', function () {
      const buf = buildFrameBuffer(StreamTrack.Video, 0, 7n, 555n, Buffer.from('hello world'));
      const state = createParserState();
      const collected: StreamFrame[] = [];

      for (let i = 0; i < buf.length; i++) {
        const chunk = buf.subarray(i, i + 1);
        for (const frame of parseFrames(state, chunk)) {
          collected.push(frame);
        }
      }

      assert.strictEqual(collected.length, 1);
      assert.strictEqual(collected[0].data.toString(), 'hello world');
      assert.strictEqual(collected[0].sequence, 7n);
    });

    it('should reassemble a frame split exactly at the header/payload boundary', function () {
      const buf = buildFrameBuffer(StreamTrack.Jpeg, 0, 3n, 9n, Buffer.from('payload-bytes'));
      const state = createParserState();
      const firstChunk = buf.subarray(0, 28);
      const secondChunk = buf.subarray(28);

      const firstBatch = [...parseFrames(state, firstChunk)];
      assert.strictEqual(firstBatch.length, 0);

      const secondBatch = [...parseFrames(state, secondChunk)];
      assert.strictEqual(secondBatch.length, 1);
      assert.strictEqual(secondBatch[0].data.toString(), 'payload-bytes');
    });

    it('should throw when the magic bytes are corrupted', function () {
      const buf = buildFrameBuffer(StreamTrack.Jpeg, 0, 0n, 0n, Buffer.from('x'));
      buf.write('XXXX', 0, 'ascii');
      const state = createParserState();

      assert.throws(() => [...parseFrames(state, buf)], /desynchronized/);
    });
  });
});
