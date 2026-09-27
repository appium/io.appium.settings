import assert from 'node:assert/strict';
import net from 'node:net';
import {describe, it} from 'node:test';

import type {ADB} from 'appium-adb';

import {StreamTrack, StreamTransport, type StreamFrame} from '../../lib/utils/index.js';

function buildFrameBuffer(
  track: StreamTrack,
  flags: number,
  sequence: bigint,
  payload: Buffer = Buffer.alloc(0),
): Buffer {
  const header = Buffer.alloc(28);
  header.write('APST', 0, 'ascii');
  header.writeUInt8(1, 4); // version
  header.writeUInt8(track, 5);
  header.writeUInt8(flags, 6);
  header.writeUInt8(0, 7); // reserved
  header.writeBigUInt64BE(sequence, 8);
  header.writeBigUInt64BE(0n, 16); // timestampMicros
  header.writeUInt32BE(payload.length, 24);
  return Buffer.concat([header, payload]);
}

const FLAG_CONFIG = 0x01;
const FLAG_KEYFRAME = 0x02;

async function listenOn(port: number): Promise<net.Server> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => resolve(server));
  });
}

async function closeServer(server: net.Server): Promise<void> {
  return new Promise((resolve) => server.close(() => resolve()));
}

// Stands in for `adb forward`: connect() calls forwardAbstractPort() expecting the
// requested host port to become connectable, so start a real listener there instead of
// actually going through adb - the on-device socket name is irrelevant to this test.
function makeFakeAdb(onConnection: (socket: net.Socket) => void): {
  adb: ADB;
  getServer: () => net.Server | undefined;
} {
  let server: net.Server | undefined;
  const adb = {
    forwardAbstractPort: async (port: number) => {
      server = await listenOn(port);
      server.on('connection', onConnection);
    },
    removePortForward: async () => {},
  } as unknown as ADB;
  return {adb, getServer: () => server};
}

async function collect(transport: StreamTransport, count: number, timeoutMs = 2000): Promise<StreamFrame[]> {
  const collected: StreamFrame[] = [];
  const deadline = Date.now() + timeoutMs;
  for await (const frame of transport.frames()) {
    collected.push(frame);
    if (collected.length >= count || Date.now() > deadline) {
      break;
    }
  }
  return collected;
}

describe('StreamTransport', function () {
  it('should suppress video frames left undecodable by an on-device queue drop, detected via a sequence gap', async function () {
    // Reproduces upstream loss: the on-device FrameQueue drops a keyframe under
    // backpressure before the host ever receives it, so the wire shows CONFIG(seq 0)
    // followed directly by dependent frames at seq 2+ - seq 1 (the dropped keyframe)
    // never arrives. Without gap detection, the host would forward all of these even
    // though none are decodable without the missing keyframe.
    const probe = await listenOn(0);
    const freePort = (probe.address() as net.AddressInfo).port;
    await closeServer(probe);

    const {adb, getServer} = makeFakeAdb((socket) => {
      socket.write(buildFrameBuffer(StreamTrack.Video, FLAG_CONFIG, 0n));
      for (let seq = 2n; seq <= 60n; seq++) {
        socket.write(buildFrameBuffer(StreamTrack.Video, 0, seq));
      }
      socket.write(buildFrameBuffer(StreamTrack.Audio, 0, 0n));
    });

    const transport = await StreamTransport.connect(adb, 'fake-socket', {localPort: freePort});
    try {
      const frames = await collect(transport, 2);
      assert.deepStrictEqual(
        frames.map((f) => `${StreamTrack[f.track]}:${f.sequence}`),
        ['Video:0', 'Audio:0'],
        'expected every dependent video frame after the gap to be discarded, leaving only config and audio',
      );
    } finally {
      await transport.close();
      const server = getServer();
      if (server) {
        await closeServer(server);
      }
    }
  });

  it('should resume forwarding once a keyframe arrives after a gap', async function () {
    const probe = await listenOn(0);
    const freePort = (probe.address() as net.AddressInfo).port;
    await closeServer(probe);

    const {adb, getServer} = makeFakeAdb((socket) => {
      socket.write(buildFrameBuffer(StreamTrack.Video, FLAG_CONFIG, 0n));
      // seq 1 (a keyframe) is missing - dropped on-device - so seq 2 arrives as a gap.
      socket.write(buildFrameBuffer(StreamTrack.Video, FLAG_KEYFRAME, 2n));
      socket.write(buildFrameBuffer(StreamTrack.Video, 0, 3n));
    });

    const transport = await StreamTransport.connect(adb, 'fake-socket', {localPort: freePort});
    try {
      const frames = await collect(transport, 3);
      assert.deepStrictEqual(
        frames.map((f) => f.sequence),
        [0n, 2n, 3n],
        'expected the keyframe at seq 2 to clear the gap and resume normal forwarding',
      );
    } finally {
      await transport.close();
      const server = getServer();
      if (server) {
        await closeServer(server);
      }
    }
  });
});
