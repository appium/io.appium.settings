import {randomUUID} from 'node:crypto';

import type {ADB} from 'appium-adb';
import {waitForCondition} from 'asyncbox';

import type {SettingsApp} from '../client.js';
import {
  JPEG_STREAM_ACTION_START,
  JPEG_STREAM_ACTION_STOP,
  JPEG_STREAM_SERVICE_NAME,
  SETTINGS_HELPER_ID,
  STREAMING_ACTIVITY_NAME,
} from '../constants.js';
import {StreamTransport} from './streaming-transport.js';
import type {JpegFrame, StartJpegStreamOpts} from './types.js';

const STREAM_STARTUP_TIMEOUT_MS = 3 * 1000;
const STREAM_STOP_TIMEOUT_MS = 3 * 1000;

/**
 * Creates a new instance of the live JPEG frame streaming session.
 * The stream only works since Android API 29+
 *
 * @returns The streaming session instance
 */
export function makeJpegStreamSession(this: SettingsApp): JpegStreamSession {
  return new JpegStreamSession(this.adb);
}

function validateJpegStreamOpts(opts: StartJpegStreamOpts): void {
  const {fps, quality, scale} = opts;
  if (fps !== undefined && (!Number.isInteger(fps) || fps <= 0)) {
    throw new TypeError(`fps must be a positive integer, got ${fps}`);
  }
  if (quality !== undefined && (!Number.isInteger(quality) || quality < 1 || quality > 100)) {
    throw new TypeError(`quality must be an integer between 1 and 100, got ${quality}`);
  }
  if (scale !== undefined && (!Number.isInteger(scale) || scale < 1 || scale > 100)) {
    throw new TypeError(`scale must be an integer between 1 and 100, got ${scale}`);
  }
}

/**
 * Live JPEG frame streaming session for the device screen.
 * This class provides methods to start, stop, and consume a continuous
 * sequence of JPEG-encoded frames over a local socket (API 29+).
 */
export class JpegStreamSession {
  private readonly adb: ADB;
  private transport: StreamTransport | null = null;

  /**
   * Creates a new JpegStreamSession instance.
   *
   * @param adb - ADB instance for device communication
   */
  constructor(adb: ADB) {
    this.adb = adb;
  }

  /**
   * Checks if the JPEG stream is currently running.
   */
  async isRunning(): Promise<boolean> {
    const stdout = await this.adb.shell(['dumpsys', 'activity', 'services', JPEG_STREAM_SERVICE_NAME]);
    return stdout.includes(JPEG_STREAM_SERVICE_NAME);
  }

  /**
   * Starts the live JPEG frame stream.
   * If a stream is already running, this method returns false without starting a new one.
   *
   * @param opts Streaming options including fps, quality and scale
   * @returns True if the stream was started successfully, false if already running
   * @throws {Error} If the stream fails to start within the timeout period
   */
  async start(opts: StartJpegStreamOpts = {}): Promise<boolean> {
    validateJpegStreamOpts(opts);
    if (await this.isRunning()) {
      return false;
    }

    const {fps, quality, scale} = opts;
    // Socket names are validated on-device against ^[a-zA-Z0-9._-]{1,200}$, so this
    // must not contain a "/" - unlike JPEG_STREAM_SERVICE_NAME.
    const socketName = `${SETTINGS_HELPER_ID}.jpegstream.${randomUUID()}`;
    const args = [
      'am',
      'start',
      '-n',
      STREAMING_ACTIVITY_NAME,
      '-a',
      JPEG_STREAM_ACTION_START,
      '--es',
      'socket_name',
      socketName,
    ];
    if (fps) {
      args.push('--es', 'fps', `${fps}`);
    }
    if (quality) {
      args.push('--es', 'quality', `${quality}`);
    }
    if (scale) {
      args.push('--es', 'scale', `${scale}`);
    }

    await this.adb.shell(args);
    try {
      await waitForCondition(async () => await this.isRunning(), {
        waitMs: STREAM_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });
    } catch {
      throw new Error(
        `The JPEG stream is not running after ${STREAM_STARTUP_TIMEOUT_MS}ms. ` +
          `Please check the logcat output for more details.`,
      );
    }

    this.transport = await StreamTransport.connect(this.adb, socketName);
    return true;
  }

  /**
   * Yields JPEG frames as they arrive from the device.
   * `start()` must be called before iterating.
   */
  async *frames(): AsyncGenerator<JpegFrame> {
    if (!this.transport) {
      throw new Error('The JPEG stream has not been started. Call start() first.');
    }
    for await (const frame of this.transport.frames()) {
      yield {
        data: frame.data,
        sequence: Number(frame.sequence),
        timestampMicros: Number(frame.timestampMicros),
      };
    }
  }

  /**
   * Stops the currently running JPEG stream.
   *
   * @returns True if the stream was stopped successfully, false if it was not running
   * @throws {Error} If the stream fails to stop within the timeout period
   */
  async stop(): Promise<boolean> {
    if (!(await this.isRunning())) {
      return false;
    }

    try {
      await this.adb.shell(['am', 'start', '-n', STREAMING_ACTIVITY_NAME, '-a', JPEG_STREAM_ACTION_STOP]);
      try {
        await waitForCondition(async () => !(await this.isRunning()), {
          waitMs: STREAM_STOP_TIMEOUT_MS,
          intervalMs: 300,
        });
      } catch {
        throw new Error(`The attempt to stop the current JPEG stream timed out after ${STREAM_STOP_TIMEOUT_MS}ms`);
      }
    } finally {
      if (this.transport) {
        await this.transport.close();
        this.transport = null;
      }
    }
    return true;
  }
}
