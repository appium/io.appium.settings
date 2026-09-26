/**
 * Track discriminator for a streamed frame, matching the on-device
 * `io.appium.settings.streaming.Frame.Track` enum ordinals.
 */
export enum StreamTrack {
  Jpeg = 0,
  Video = 1,
  Audio = 2,
}

const FLAG_CONFIG = 0x01;
const FLAG_KEYFRAME = 0x02;
const FLAG_EOS = 0x04;

/**
 * A single parsed frame/access-unit read off the streaming socket.
 */
export interface StreamFrame {
  track: StreamTrack;
  sequence: bigint;
  timestampMicros: bigint;
  data: Buffer;
  isConfig: boolean;
  isKeyFrame: boolean;
  isEndOfStream: boolean;
}

const HEADER_SIZE = 28;
const MAGIC = Buffer.from('APST', 'ascii');

/**
 * Incremental parser state, carried across chunk boundaries.
 */
export interface FrameParserState {
  buf: Buffer;
}

export function createParserState(): FrameParserState {
  return {buf: Buffer.alloc(0)};
}

/**
 * Parses as many complete frames as are available in `state.buf` + `chunk`,
 * carrying over any trailing partial frame in `state` for the next call.
 *
 * Wire format (big-endian, matches `io.appium.settings.streaming.StreamProtocol`):
 * offset 0 (4 bytes): magic "APST"; offset 4 (1 byte): version;
 * offset 5 (1 byte): track; offset 6 (1 byte): flags; offset 7: reserved;
 * offset 8 (8 bytes): sequence; offset 16 (8 bytes): timestampMicros;
 * offset 24 (4 bytes): payloadLength; followed by `payloadLength` payload bytes.
 *
 * @throws {Error} If the stream desynchronizes (unexpected magic bytes)
 */
export function* parseFrames(state: FrameParserState, chunk: Buffer): Generator<StreamFrame> {
  state.buf = state.buf.length ? Buffer.concat([state.buf, chunk]) : chunk;
  for (;;) {
    if (state.buf.length < HEADER_SIZE) {
      return;
    }
    if (!state.buf.subarray(0, 4).equals(MAGIC)) {
      throw new Error('Stream desynchronized: unexpected magic bytes in the streaming protocol header');
    }

    const payloadLength = state.buf.readUInt32BE(24);
    const totalLength = HEADER_SIZE + payloadLength;
    if (state.buf.length < totalLength) {
      return;
    }

    const track = state.buf.readUInt8(5) as StreamTrack;
    const flags = state.buf.readUInt8(6);
    const sequence = state.buf.readBigUInt64BE(8);
    const timestampMicros = state.buf.readBigUInt64BE(16);
    const data = Buffer.from(state.buf.subarray(HEADER_SIZE, totalLength));

    state.buf = state.buf.subarray(totalLength);

    yield {
      track,
      sequence,
      timestampMicros,
      data,
      isConfig: (flags & FLAG_CONFIG) !== 0,
      isKeyFrame: (flags & FLAG_KEYFRAME) !== 0,
      isEndOfStream: (flags & FLAG_EOS) !== 0,
    };
  }
}
