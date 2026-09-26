import net from 'node:net';

import type {ADB} from 'appium-adb';
import {retryInterval} from 'asyncbox';

import type {LocalPortOpts} from '../commands/types.js';
import {createParserState, parseFrames, type StreamFrame} from './streaming-protocol.js';

const DEFAULT_QUEUE_CAPACITY = 60;
const CONNECT_TIMEOUT_MS = 5000;
const CONNECT_RETRY_INTERVAL_MS = 200;

/**
 * A bounded async-iterable queue that drops the oldest buffered item once its
 * capacity is exceeded, so a slow consumer never causes unbounded memory growth.
 */
export class BoundedFrameQueue<T> {
  private readonly items: T[] = [];
  private readonly waiters: Array<(result: IteratorResult<T>) => void> = [];
  private readonly errorWaiters: Array<(err: Error) => void> = [];
  private ended = false;
  private endError: Error | undefined;

  constructor(private readonly capacity: number = DEFAULT_QUEUE_CAPACITY) {}

  /**
   * Enqueues an item. If a consumer is already awaiting the next item, it is
   * delivered directly; otherwise it is buffered, dropping the oldest buffered
   * item first if `capacity` would be exceeded.
   *
   * @param item - The item to enqueue
   */
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

  /**
   * Ends the queue. Any already-buffered items are still delivered first; once
   * drained, the async iterator completes, rejecting with `error` if one is given.
   * A no-op if the queue has already ended.
   *
   * @param error - Optional error to surface to the consumer once buffered items are drained
   */
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

  /**
   * Async-iterates the queue, yielding items as they are pushed until {@link end} is called.
   */
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

/**
 * Validates local-port selection options for a live stream session.
 *
 * @param opts - Options containing an optional localPort and/or localPortRange
 * @throws {TypeError} If both localPort and localPortRange are provided, or either is malformed
 */
export function validateLocalPortOpts(opts: LocalPortOpts): void {
  const {localPort, localPortRange} = opts;
  if (localPort !== undefined && localPortRange !== undefined) {
    throw new TypeError('Only one of localPort or localPortRange may be provided');
  }
  if (localPort !== undefined && (!Number.isInteger(localPort) || localPort < 1 || localPort > 65535)) {
    throw new TypeError(`localPort must be an integer between 1 and 65535, got ${localPort}`);
  }
  if (localPortRange !== undefined) {
    const [min, max] = localPortRange;
    if (!Number.isInteger(min) || !Number.isInteger(max) || min < 1 || max > 65535 || min > max) {
      throw new TypeError(
        `localPortRange must be a [min, max] tuple with 1 <= min <= max <= 65535, got [${min}, ${max}]`,
      );
    }
  }
}

/**
 * Tries to bind a local TCP server to `port` (0 lets the OS pick an ephemeral one),
 * immediately closes it, and returns the bound port - or `null` if `port` is already
 * in use (EADDRINUSE/EACCES), so the caller can try another candidate.
 */
async function tryBindPort(port: number): Promise<number | null> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.unref();
    server.once('error', (err: NodeJS.ErrnoException) => {
      if (err.code === 'EADDRINUSE' || err.code === 'EACCES') {
        resolve(null);
      } else {
        reject(err);
      }
    });
    server.listen(port, '127.0.0.1', () => {
      const address = server.address();
      const boundPort = address && typeof address === 'object' ? address.port : null;
      server.close(() => {
        if (boundPort) {
          resolve(boundPort);
        } else {
          reject(new Error('Unable to determine a free local port'));
        }
      });
    });
  });
}

/**
 * Resolves the local TCP port to use for the `adb forward` bridge: the exact
 * `localPort` if given, the first free port in `localPortRange` if given, or an
 * OS-assigned ephemeral port otherwise.
 *
 * @param opts - Port selection options; see {@link validateLocalPortOpts}
 * @returns The bound, free local TCP port
 * @throws {Error} If the requested port is busy, or no free port exists in the requested range
 */
export async function resolveLocalPort(opts: LocalPortOpts): Promise<number> {
  const {localPort, localPortRange} = opts;

  if (localPort !== undefined) {
    const bound = await tryBindPort(localPort);
    if (bound === null) {
      throw new Error(`Local port ${localPort} is already in use`);
    }
    return bound;
  }

  if (localPortRange) {
    const [min, max] = localPortRange;
    for (let port = min; port <= max; port++) {
      const bound = await tryBindPort(port);
      if (bound !== null) {
        return bound;
      }
    }
    throw new Error(`No free local port found in range ${min}-${max}`);
  }

  // No preference given - let the OS assign an ephemeral port.
  const bound = await tryBindPort(0);
  if (bound === null) {
    throw new Error('Unable to determine a free local port');
  }
  return bound;
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
  private readonly queue = new BoundedFrameQueue<StreamFrame>();
  private readonly parserState = createParserState();
  private closed = false;

  private constructor(
    private readonly adb: ADB,
    private readonly localPort: number,
    private readonly socket: net.Socket,
  ) {}

  /**
   * Resolves a local port, forwards it to the on-device local abstract socket via
   * `adb forward`, and connects to it (retrying until the on-device session accepts).
   * On failure, any port forward that was already set up is best-effort removed.
   *
   * @param adb - ADB instance for device communication
   * @param socketName - Name of the on-device local abstract socket to forward
   * @param portOpts - Optional local port selection options
   * @returns A connected StreamTransport instance
   * @throws {Error} If the local port cannot be resolved, or the connection fails
   */
  static async connect(adb: ADB, socketName: string, portOpts: LocalPortOpts = {}): Promise<StreamTransport> {
    const localPort = await resolveLocalPort(portOpts);
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

  /**
   * Returns an async generator yielding parsed frames as they arrive on the socket,
   * until the connection closes or errors.
   */
  frames(): AsyncGenerator<StreamFrame> {
    return this.queue[Symbol.asyncIterator]();
  }

  /**
   * Destroys the client socket and best-effort removes the `adb forward` port
   * mapping. Safe to call more than once.
   */
  async close(): Promise<void> {
    if (this.closed) {
      return;
    }
    this.closed = true;
    this.socket.destroy();
    await this.adb.removePortForward(this.localPort).catch(() => {});
  }
}
