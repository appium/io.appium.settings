import net from 'node:net';

import type {ADB} from 'appium-adb';
import {retryInterval} from 'asyncbox';

import type {LocalPortOpts} from '../commands/types.js';
import {createParserState, parseFrames, StreamTrack, type StreamFrame} from './streaming-protocol.js';

const DEFAULT_QUEUE_CAPACITY = 60;
const CONNECT_TIMEOUT_MS = 5000;
const CONNECT_RETRY_INTERVAL_MS = 200;
// adb forward's host-side TCP listener accepts a connection immediately, independent of
// whether the on-device local socket is bound yet; if it isn't, adb tears the connection
// down right away with no data. Wait this long past 'connect' before trusting the socket.
const CONNECT_VERIFY_GRACE_MS = 150;

/**
 * Describes a decode-dependency chain within the queued items (e.g. H.264/HEVC video
 * frames, where a delta frame is undecodable without every frame back to its last
 * keyframe). Items outside the chain (e.g. an interleaved audio track) are unaffected.
 */
export interface DependencyChain<T> {
  /** True for items that participate in the chain at all (e.g. non-CONFIG video frames). */
  isChainMember: (item: T) => boolean;
  /** True for a chain member that doesn't depend on earlier ones (e.g. a keyframe). */
  isSyncPoint: (item: T) => boolean;
}

/**
 * A bounded async-iterable queue that drops the oldest buffered item once its
 * capacity is exceeded, so a slow consumer never causes unbounded memory growth.
 * An optional `isProtected` predicate can shield items (e.g. video CONFIG frames)
 * from eviction as long as any non-protected item remains to drop instead. An
 * optional `dependencyChain` additionally makes eviction dependency-aware: dropping
 * any chain member cascades to every following chain member up to (not including)
 * the next sync point, and further chain-member pushes are discarded until a fresh
 * sync point arrives - since anything in between is undecodable, buffering it only
 * to have the consumer choke on it later isn't useful.
 */
export class BoundedFrameQueue<T> {
  private readonly items: T[] = [];
  private readonly waiters: Array<(result: IteratorResult<T>) => void> = [];
  private readonly errorWaiters: Array<(err: Error) => void> = [];
  private ended = false;
  private endError: Error | undefined;
  private awaitingSyncPoint = false;

  constructor(
    private readonly capacity: number = DEFAULT_QUEUE_CAPACITY,
    private readonly isProtected?: (item: T) => boolean,
    private readonly dependencyChain?: DependencyChain<T>,
  ) {}

  /**
   * Enqueues an item. If a consumer is already awaiting the next item, it is
   * delivered directly; otherwise it is buffered, dropping the oldest droppable
   * (non-protected) buffered item first if `capacity` would be exceeded. If a
   * `dependencyChain` was given and a prior eviction broke it without a later sync
   * point already buffered, chain-member items are silently discarded here until a
   * fresh sync point item is pushed.
   *
   * @param item - The item to enqueue
   */
  push(item: T): void {
    if (this.ended) {
      return;
    }
    const chain = this.dependencyChain;
    if (this.awaitingSyncPoint && chain?.isChainMember(item)) {
      if (!chain.isSyncPoint(item)) {
        return;
      }
      this.awaitingSyncPoint = false;
    }

    const waiter = this.waiters.shift();
    if (waiter) {
      this.errorWaiters.shift();
      waiter({value: item, done: false});
      return;
    }
    this.items.push(item);
    while (this.items.length > this.capacity) {
      this.evictOnce();
    }
  }

  /**
   * Marks the dependency chain (if configured) as broken by something outside this
   * queue's own eviction - e.g. a producer detecting a sequence gap in items it never
   * even offered here. The next chain-member item pushed is discarded unless it is
   * itself a sync point, exactly as if a local eviction had cascaded off the end of
   * the buffer. A no-op if no `dependencyChain` was configured.
   */
  notifyGap(): void {
    if (this.dependencyChain) {
      this.awaitingSyncPoint = true;
    }
  }

  /**
   * Evicts one item to bring the queue back under capacity: the oldest non-protected
   * item, or (if every buffered item is protected, which should not happen in
   * practice) the true oldest one regardless. If the evicted item was a
   * `dependencyChain` member, cascades to drop every following chain member up to
   * the next sync point, setting {@link awaitingSyncPoint} if none is found.
   */
  private evictOnce(): void {
    const isProtected = this.isProtected;
    const index = isProtected ? this.items.findIndex((item) => !isProtected(item)) : 0;
    if (index === -1) {
      this.items.shift();
      return;
    }
    const [evicted] = this.items.splice(index, 1);
    const chain = this.dependencyChain;
    if (chain?.isChainMember(evicted) && !this.dropDependentsFrom(index, chain)) {
      this.awaitingSyncPoint = true;
    }
  }

  /**
   * Starting at `index`, removes chain-member items up to (not including) the next
   * sync point, skipping over non-chain-member items (e.g. an interleaved audio
   * track) in place without disturbing them.
   *
   * @returns True if a sync point was found (and kept), false if the scan reached
   * the end of the buffer first
   */
  private dropDependentsFrom(index: number, chain: DependencyChain<T>): boolean {
    while (index < this.items.length) {
      const item = this.items[index];
      if (!chain.isChainMember(item)) {
        index++;
        continue;
      }
      if (chain.isSyncPoint(item)) {
        return true;
      }
      this.items.splice(index, 1);
    }
    return false;
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
    let settled = false;
    let verifyTimer: NodeJS.Timeout | undefined;

    const cleanup = () => {
      socket.removeListener('error', onError);
      socket.removeListener('close', onClose);
      clearTimeout(verifyTimer);
    };
    const onError = (err: Error) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      socket.destroy();
      reject(err);
    };
    const onClose = () => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      reject(new Error('Connection was closed before the on-device stream became ready'));
    };
    socket.once('error', onError);
    socket.once('close', onClose);
    socket.once('connect', () => {
      verifyTimer = setTimeout(() => {
        if (settled) {
          return;
        }
        settled = true;
        cleanup();
        resolve(socket);
      }, CONNECT_VERIFY_GRACE_MS);
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
  // Config frames (e.g. video SPS/PPS) carry decoder state a consumer needs to make sense
  // of everything that follows, so protect them from the bounded queue's drop-oldest policy.
  // Non-config video frames form a decode-dependency chain (a delta frame is undecodable
  // without every frame back to its last keyframe), so eviction there must cascade up to
  // the next keyframe rather than leaving orphaned, undecodable frames behind - audio
  // frames have no such dependency and are left alone by this either way.
  private readonly queue = new BoundedFrameQueue<StreamFrame>(DEFAULT_QUEUE_CAPACITY, (frame) => frame.isConfig, {
    isChainMember: (frame) => frame.track === StreamTrack.Video && !frame.isConfig,
    isSyncPoint: (frame) => frame.isKeyFrame,
  });
  private readonly parserState = createParserState();
  private closed = false;
  // Tracks the video track's shared sequence counter (CONFIG and data frames both draw
  // from it) so a gap - meaning the on-device FrameQueue dropped a frame under
  // backpressure before we ever saw it - can be detected on receipt, not just when our
  // own queue evicts something locally.
  private lastVideoSequence: bigint | undefined;

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
          if (frame.track === StreamTrack.Video) {
            if (this.lastVideoSequence !== undefined && frame.sequence !== this.lastVideoSequence + 1n) {
              this.queue.notifyGap();
            }
            this.lastVideoSequence = frame.sequence;
          }
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
