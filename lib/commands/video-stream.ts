import {randomUUID} from 'node:crypto';

import type {ADB} from 'appium-adb';
import {waitForCondition} from 'asyncbox';

import type {SettingsApp} from '../client.js';
import {
  STREAMING_ACTIVITY_NAME,
  VIDEO_STREAM_ACTION_START,
  VIDEO_STREAM_ACTION_STOP,
  VIDEO_STREAM_SERVICE_NAME,
} from '../constants.js';
import {StreamTrack} from './streaming-protocol.js';
import {StreamTransport} from './streaming-transport.js';
import type {AccessUnit, StartVideoStreamOpts} from './types.js';

const STREAM_STARTUP_TIMEOUT_MS = 3 * 1000;
const STREAM_STOP_TIMEOUT_MS = 3 * 1000;

/**
 * Creates a new instance of the live H.264/HEVC video streaming session.
 * The stream only works since Android API 29+
 *
 * @returns The streaming session instance
 */
export function makeVideoStreamSession(this: SettingsApp): VideoStreamSession {
  return new VideoStreamSession(this.adb);
}

function validateVideoStreamOpts(opts: StartVideoStreamOpts): void {
  const {codec, fps, bitrate} = opts;
  if (codec !== undefined && codec !== 'h264' && codec !== 'hevc') {
    throw new TypeError(`codec must be either 'h264' or 'hevc', got ${codec}`);
  }
  if (fps !== undefined && (!Number.isInteger(fps) || fps <= 0)) {
    throw new TypeError(`fps must be a positive integer, got ${fps}`);
  }
  if (bitrate !== undefined && (!Number.isInteger(bitrate) || bitrate <= 0)) {
    throw new TypeError(`bitrate must be a positive integer, got ${bitrate}`);
  }
}

/**
 * Live H.264/HEVC video streaming session for the device screen, with an
 * optional interleaved AAC audio track.
 */
export class VideoStreamSession {
  private readonly adb: ADB;
  private transport: StreamTransport | null = null;

  /**
   * Creates a new VideoStreamSession instance.
   *
   * @param adb - ADB instance for device communication
   */
  constructor(adb: ADB) {
    this.adb = adb;
  }

  /**
   * Checks if the video stream is currently running.
   */
  async isRunning(): Promise<boolean> {
    const stdout = await this.adb.shell(['dumpsys', 'activity', 'services', VIDEO_STREAM_SERVICE_NAME]);
    return stdout.includes(VIDEO_STREAM_SERVICE_NAME);
  }

  /**
   * Starts the live video stream.
   * If a stream is already running, this method returns false without starting a new one.
   *
   * @param opts Streaming options including codec, fps, bitrate, resolution and audio
   * @returns True if the stream was started successfully, false if already running
   * @throws {Error} If the stream fails to start within the timeout period
   */
  async start(opts: StartVideoStreamOpts = {}): Promise<boolean> {
    validateVideoStreamOpts(opts);
    if (await this.isRunning()) {
      return false;
    }

    const {codec, fps, bitrate, resolution, audio} = opts;
    const socketName = `${VIDEO_STREAM_SERVICE_NAME}.${randomUUID()}`;
    const args = [
      'am',
      'start',
      '-n',
      STREAMING_ACTIVITY_NAME,
      '-a',
      VIDEO_STREAM_ACTION_START,
      '--es',
      'socket_name',
      socketName,
    ];
    if (codec) {
      args.push('--es', 'codec', codec);
    }
    if (fps) {
      args.push('--es', 'fps', `${fps}`);
    }
    if (bitrate) {
      args.push('--es', 'bitrate', `${bitrate}`);
    }
    if (resolution) {
      args.push('--es', 'resolution', resolution);
    }
    if (audio !== undefined) {
      args.push('--es', 'audio', audio ? 'true' : 'false');
    }

    await this.adb.shell(args);
    try {
      await waitForCondition(async () => await this.isRunning(), {
        waitMs: STREAM_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });
    } catch {
      throw new Error(
        `The video stream is not running after ${STREAM_STARTUP_TIMEOUT_MS}ms. ` +
          `Please check the logcat output for more details.`,
      );
    }

    this.transport = await StreamTransport.connect(this.adb, socketName);
    return true;
  }

  /**
   * Yields access units (video NAL units and, if enabled, AAC audio units) as
   * they arrive from the device. `start()` must be called before iterating.
   */
  async *accessUnits(): AsyncGenerator<AccessUnit> {
    if (!this.transport) {
      throw new Error('The video stream has not been started. Call start() first.');
    }
    for await (const frame of this.transport.frames()) {
      yield {
        track: frame.track === StreamTrack.Audio ? 'audio' : 'video',
        data: frame.data,
        sequence: Number(frame.sequence),
        timestampMicros: Number(frame.timestampMicros),
        isKeyFrame: frame.isKeyFrame,
        isConfig: frame.isConfig,
      };
    }
  }

  /**
   * Stops the currently running video stream.
   *
   * @returns True if the stream was stopped successfully, false if it was not running
   * @throws {Error} If the stream fails to stop within the timeout period
   */
  async stop(): Promise<boolean> {
    if (!(await this.isRunning())) {
      return false;
    }

    try {
      await this.adb.shell(['am', 'start', '-n', STREAMING_ACTIVITY_NAME, '-a', VIDEO_STREAM_ACTION_STOP]);
      try {
        await waitForCondition(async () => !(await this.isRunning()), {
          waitMs: STREAM_STOP_TIMEOUT_MS,
          intervalMs: 300,
        });
      } catch {
        throw new Error(`The attempt to stop the current video stream timed out after ${STREAM_STOP_TIMEOUT_MS}ms`);
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
