import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {afterEach, beforeEach, describe, it} from 'node:test';

import {ADB} from 'appium-adb';
import sinon from 'sinon';

import {SettingsApp} from '../../lib/client.js';
import {MEDIA_SCAN_ACTION, MEDIA_SCAN_RECEIVER} from '../../lib/constants.js';

describe('scanMedia', function () {
  const sandbox = sinon.createSandbox();
  let client: SettingsApp;
  beforeEach(function () {
    client = new SettingsApp({adb: new ADB()});
  });
  afterEach(function () {
    sandbox.restore();
  });

  it('quotes the complete path in the broadcast extra', async function () {
    const broadcast = sandbox.stub(client, 'checkBroadcast').resolves('');
    await client.scanMedia("/sdcard/O'Brien!.png");
    sinon.assert.calledOnceWithExactly(
      broadcast,
      ['-n', MEDIA_SCAN_RECEIVER, '-a', MEDIA_SCAN_ACTION, '--es', 'path', `'/sdcard/O'"'"'Brien!.png'`],
      'scan media',
    );
  });

  it('preserves special characters through the device shell', {skip: process.platform === 'win32'}, async function () {
    const values = ['', '/sdcard/two words.png', '/sdcard/O\'Brien! "$HOME" `printf unexpected`;\n[*].png'];
    const broadcast = sandbox.stub(client, 'checkBroadcast').resolves('');
    for (const destination of values) {
      await client.scanMedia(destination);
      const args = broadcast.lastCall.args[0];
      const output = execFileSync('/bin/sh', ['-c', `printf '%s\\0' ${args.join(' ')}`]);
      assert.deepStrictEqual(output.toString().split('\0').slice(0, -1), [
        '-n',
        MEDIA_SCAN_RECEIVER,
        '-a',
        MEDIA_SCAN_ACTION,
        '--es',
        'path',
        destination,
      ]);
    }
  });
});
