import assert from 'node:assert/strict';
import {describe, it, beforeEach, afterEach} from 'node:test';

import {ADB} from 'appium-adb';
import sinon from 'sinon';

import {JpegStreamSession} from '../../lib/commands/jpeg-stream.js';
import {StreamTransport} from '../../lib/commands/streaming-transport.js';
import {VideoStreamSession} from '../../lib/commands/video-stream.js';
import {JPEG_STREAM_SERVICE_NAME, VIDEO_STREAM_SERVICE_NAME} from '../../lib/constants.js';

describe('streaming sessions', function () {
  let sandbox: sinon.SinonSandbox;
  let adb: ADB;

  beforeEach(function () {
    sandbox = sinon.createSandbox();
    adb = new ADB({});
  });

  afterEach(function () {
    sandbox.restore();
  });

  describe('JpegStreamSession', function () {
    it('should reject an invalid fps value', async function () {
      const session = new JpegStreamSession(adb);
      await assert.rejects(() => session.start({fps: 0}), TypeError);
    });

    it('should reject an invalid quality value', async function () {
      const session = new JpegStreamSession(adb);
      await assert.rejects(() => session.start({quality: 101}), TypeError);
    });

    it('should reject an invalid scale value', async function () {
      const session = new JpegStreamSession(adb);
      await assert.rejects(() => session.start({scale: 0}), TypeError);
    });

    it('should report isRunning() based on dumpsys output', async function () {
      const session = new JpegStreamSession(adb);
      sandbox
        .stub(adb, 'shell')
        .withArgs(['dumpsys', 'activity', 'services', JPEG_STREAM_SERVICE_NAME])
        .resolves(`ServiceRecord{x u0 ${JPEG_STREAM_SERVICE_NAME}}`);
      assert.strictEqual(await session.isRunning(), true);
    });

    it('should return false from start() when already running', async function () {
      const session = new JpegStreamSession(adb);
      sandbox
        .stub(adb, 'shell')
        .withArgs(['dumpsys', 'activity', 'services', JPEG_STREAM_SERVICE_NAME])
        .resolves(`ServiceRecord{x u0 ${JPEG_STREAM_SERVICE_NAME}}`);
      assert.strictEqual(await session.start(), false);
    });

    it('should return false from stop() when not running', async function () {
      const session = new JpegStreamSession(adb);
      sandbox.stub(adb, 'shell').resolves('');
      assert.strictEqual(await session.stop(), false);
    });

    it('should build the expected am start argv and connect a transport', async function () {
      let dumpsysCalls = 0;
      const shellStub = sandbox.stub(adb, 'shell').callsFake(async (args: unknown) => {
        const argv = args as string[];
        if (argv[0] === 'dumpsys') {
          dumpsysCalls++;
          return dumpsysCalls === 1 ? '' : `ServiceRecord{x u0 ${JPEG_STREAM_SERVICE_NAME}}`;
        }
        return '';
      });
      sandbox.stub(StreamTransport, 'connect').resolves({} as unknown as StreamTransport);

      const session = new JpegStreamSession(adb);
      const started = await session.start({fps: 10, quality: 60, scale: 50});
      assert.strictEqual(started, true);

      const amStartCall = shellStub
        .getCalls()
        .map((c) => c.args[0] as string[])
        .find((a) => a[0] === 'am');
      assert.ok(amStartCall, 'expected an "am start" shell call');
      const argsStr = amStartCall!.join(' ');
      assert.match(argsStr, /-a io\.appium\.settings\.streaming\.jpeg\.ACTION_START/);
      assert.match(argsStr, /--es fps 10/);
      assert.match(argsStr, /--es quality 60/);
      assert.match(argsStr, /--es scale 50/);
    });
  });

  describe('VideoStreamSession', function () {
    it('should reject an invalid codec value', async function () {
      const session = new VideoStreamSession(adb);
      await assert.rejects(() => session.start({codec: 'vp9' as any}), TypeError);
    });

    it('should reject an invalid fps value', async function () {
      const session = new VideoStreamSession(adb);
      await assert.rejects(() => session.start({fps: -1}), TypeError);
    });

    it('should reject an invalid bitrate value', async function () {
      const session = new VideoStreamSession(adb);
      await assert.rejects(() => session.start({bitrate: 0}), TypeError);
    });

    it('should report isRunning() based on dumpsys output', async function () {
      const session = new VideoStreamSession(adb);
      sandbox
        .stub(adb, 'shell')
        .withArgs(['dumpsys', 'activity', 'services', VIDEO_STREAM_SERVICE_NAME])
        .resolves(`ServiceRecord{x u0 ${VIDEO_STREAM_SERVICE_NAME}}`);
      assert.strictEqual(await session.isRunning(), true);
    });

    it('should return false from start() when already running', async function () {
      const session = new VideoStreamSession(adb);
      sandbox
        .stub(adb, 'shell')
        .withArgs(['dumpsys', 'activity', 'services', VIDEO_STREAM_SERVICE_NAME])
        .resolves(`ServiceRecord{x u0 ${VIDEO_STREAM_SERVICE_NAME}}`);
      assert.strictEqual(await session.start(), false);
    });

    it('should return false from stop() when not running', async function () {
      const session = new VideoStreamSession(adb);
      sandbox.stub(adb, 'shell').resolves('');
      assert.strictEqual(await session.stop(), false);
    });

    it('should build the expected am start argv and connect a transport', async function () {
      let dumpsysCalls = 0;
      const shellStub = sandbox.stub(adb, 'shell').callsFake(async (args: unknown) => {
        const argv = args as string[];
        if (argv[0] === 'dumpsys') {
          dumpsysCalls++;
          return dumpsysCalls === 1 ? '' : `ServiceRecord{x u0 ${VIDEO_STREAM_SERVICE_NAME}}`;
        }
        return '';
      });
      sandbox.stub(StreamTransport, 'connect').resolves({} as unknown as StreamTransport);

      const session = new VideoStreamSession(adb);
      const started = await session.start({codec: 'hevc', fps: 15, bitrate: 1000000, audio: true});
      assert.strictEqual(started, true);

      const amStartCall = shellStub
        .getCalls()
        .map((c) => c.args[0] as string[])
        .find((a) => a[0] === 'am');
      assert.ok(amStartCall, 'expected an "am start" shell call');
      const argsStr = amStartCall!.join(' ');
      assert.match(argsStr, /-a io\.appium\.settings\.streaming\.video\.ACTION_START/);
      assert.match(argsStr, /--es codec hevc/);
      assert.match(argsStr, /--es fps 15/);
      assert.match(argsStr, /--es bitrate 1000000/);
      assert.match(argsStr, /--es audio true/);
    });
  });
});
