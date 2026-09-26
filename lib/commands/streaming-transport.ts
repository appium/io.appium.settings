import net from 'node:net';

import type {ADB} from 'appium-adb';
import {retryInterval} from 'asyncbox';

import {createParserState, parseFrames, type StreamFrame} from './streaming-protocol.js';

const DEFAULT_QUEUE_CAPACITY = 60;
const CONNECT_TIMEOUT_MS = 5000;
const CONNECT_RETRY_INTERVAL_MS = 200;

/**
 * A bounded async-iterable queue that drops the oldest buffered item once its
 * capacity is exceeded, so a slow consumer never causes unbounded memory growth.
 */
export class BoundedFrameQueue<T> {
  private readonly capacity: number;
  private readonly items: T[] = [];
  private readonly waiters: Array<(result: IteratorResult<T>) => void> = [];
  private readonly errorWaiters: Array<(err: Error) => void> = [];
  private ended = false;
  private endError: Error | undefined;

  constructor(capacity: number = DEFAULT_QUEUE_CAPACITY) {
    this.capacity = capacity;
  }

  push(item: T): void {
    if (this.ended) {
      return;
    }
    const waiter = this.waiters.shift();
    if (waiter) {
      this.errorWaiters.shift();
      waiter({value: item, done: false});
      return;
    }
    this.items.push(item);
    while (this.items.length > this.capacity) {
      this.items.shift();
    }
  }

  end(error?: Error): void {
    if (this.ended) {
      return;
    }
    this.ended = true;
    this.endError = error;
    for (;;) {
      const waiter = this.waiters.shift();
      const errorWaiter = this.errorWaiters.shift();
      if (!waiter || !errorWaiter) {
        return;
      }
      if (error) {
        errorWaiter(error);
      } else {
        waiter({value: undefined, done: true});
      }
    }
  }

  private next(): Promise<IteratorResult<T>> {
    if (this.items.length > 0) {
      return Promise.resolve({value: this.items.shift() as T, done: false});
    }
    if (this.ended) {
      return this.endError ? Promise.reject(this.endError) : Promise.resolve({value: undefined, done: true});
    }
    return new Promise((resolve, reject) => {
      this.waiters.push(resolve);
      this.errorWaiters.push(reject);
    });
  }

  async *[Symbol.asyncIterator](): AsyncGenerator<T> {
    for (;;) {
      const result = await this.next();
      if (result.done) {
        return;
      }
      yield result.value;
    }
  }
}

async function pickFreePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = address && typeof address === 'object' ? address.port : null;
      server.close(() => {
        if (port) {
          resolve(port);
        } else {
          reject(new Error('Unable to determine a free local port'));
        }
      });
    });
  });
}

async function connectOnce(port: number): Promise<net.Socket> {
  return new Promise<net.Socket>((resolve, reject) => {
    const socket = net.connect(port, '127.0.0.1');
    socket.once('connect', () => resolve(socket));
    socket.once('error', (err) => {
      socket.destroy();
      reject(err);
    });
  });
}

async function connectWithRetry(port: number, timeoutMs: number, intervalMs: number): Promise<net.Socket> {
  const times = Math.max(1, Math.ceil(timeoutMs / intervalMs));
  let socket: net.Socket | null;
  try {
    socket = await retryInterval(times, intervalMs, connectOnce, port);
  } catch (e) {
    throw new Error(
      `Could not connect to the local streaming socket on port ${port} after ${times} attempts. ` +
        `Last error: ${e instanceof Error ? e.message : e}`,
      {cause: e},
    );
  }
  if (!socket) {
    throw new Error(`Could not connect to the local streaming socket on port ${port}`);
  }
  return socket;
}

/**
 * Bridges the on-device local abstract socket to the host over `adb forward`,
 * parses the streaming wire protocol, and exposes the result as a bounded
 * async-iterable frame queue.
 */
export class StreamTransport {
  private readonly adb: ADB;
  private readonly localPort: number;
  private readonly socket: net.Socket;
  private readonly queue = new BoundedFrameQueue<StreamFrame>();
  private readonly parserState = createParserState();
  private closed = false;

  private constructor(adb: ADB, localPort: number, socket: net.Socket) {
    this.adb = adb;
    this.localPort = localPort;
    this.socket = socket;
  }

  static async connect(adb: ADB, socketName: string): Promise<StreamTransport> {
    const localPort = await pickFreePort();
    await adb.forwardAbstractPort(localPort, socketName);
    let socket: net.Socket;
    try {
      socket = await connectWithRetry(localPort, CONNECT_TIMEOUT_MS, CONNECT_RETRY_INTERVAL_MS);
    } catch (e) {
      await adb.removePortForward(localPort).catch(() => {});
      throw e;
    }
    const transport = new StreamTransport(adb, localPort, socket);
    transport.wire();
    return transport;
  }

  private wire(): void {
    this.socket.on('data', (chunk: Buffer) => {
      try {
        for (const frame of parseFrames(this.parserState, chunk)) {
          this.queue.push(frame);
        }
      } catch (e) {
        this.queue.end(e instanceof Error ? e : new Error(String(e)));
        this.socket.destroy();
      }
    });
    this.socket.on('close', () => this.queue.end());
    this.socket.on('error', (err) => this.queue.end(err));
  }

  frames(): AsyncGenerator<StreamFrame> {
    return this.queue[Symbol.asyncIterator]();
  }

  async close(): Promise<void> {
    if (this.closed) {
      return;
    }
    this.closed = true;
    this.socket.destroy();
    await this.adb.removePortForward(this.localPort).catch(() => {});
  }
}
