import assert from 'node:assert/strict';
import net from 'node:net';
import {describe, it} from 'node:test';

import {resolveLocalPort, validateLocalPortOpts} from '../../lib/commands/streaming-transport.js';

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

describe('validateLocalPortOpts', function () {
  it('should accept no port options', function () {
    assert.doesNotThrow(() => validateLocalPortOpts({}));
  });

  it('should accept a valid localPort', function () {
    assert.doesNotThrow(() => validateLocalPortOpts({localPort: 12345}));
  });

  it('should accept a valid localPortRange', function () {
    assert.doesNotThrow(() => validateLocalPortOpts({localPortRange: [12000, 12010]}));
  });

  it('should reject both localPort and localPortRange being provided', function () {
    assert.throws(() => validateLocalPortOpts({localPort: 12345, localPortRange: [12000, 12010]}), TypeError);
  });

  it('should reject a non-integer localPort', function () {
    assert.throws(() => validateLocalPortOpts({localPort: 12345.5}), TypeError);
  });

  it('should reject a localPort out of range', function () {
    assert.throws(() => validateLocalPortOpts({localPort: 0}), TypeError);
    assert.throws(() => validateLocalPortOpts({localPort: 70000}), TypeError);
  });

  it('should reject a localPortRange with min > max', function () {
    assert.throws(() => validateLocalPortOpts({localPortRange: [12010, 12000]}), TypeError);
  });

  it('should reject a localPortRange with out-of-bounds values', function () {
    assert.throws(() => validateLocalPortOpts({localPortRange: [0, 100]}), TypeError);
    assert.throws(() => validateLocalPortOpts({localPortRange: [100, 70000]}), TypeError);
  });
});

describe('resolveLocalPort', function () {
  it('should return an OS-assigned port when no preference is given', async function () {
    const port = await resolveLocalPort({});
    assert.ok(Number.isInteger(port) && port > 0);
  });

  it('should return the exact requested localPort when it is free', async function () {
    // Grab a genuinely free ephemeral port first, then release it and request it explicitly.
    const probe = await listenOn(0);
    const freePort = (probe.address() as net.AddressInfo).port;
    await closeServer(probe);

    const port = await resolveLocalPort({localPort: freePort});
    assert.strictEqual(port, freePort);
  });

  it('should reject when the exact requested localPort is already in use', async function () {
    const server = await listenOn(0);
    const busyPort = (server.address() as net.AddressInfo).port;
    try {
      await assert.rejects(() => resolveLocalPort({localPort: busyPort}), /already in use/);
    } finally {
      await closeServer(server);
    }
  });

  it('should pick the first free port in localPortRange, skipping busy ones', async function () {
    const probe = await listenOn(0);
    const basePort = (probe.address() as net.AddressInfo).port;
    await closeServer(probe);

    const busy = await listenOn(basePort);
    try {
      const port = await resolveLocalPort({localPortRange: [basePort, basePort + 5]});
      assert.notStrictEqual(port, basePort);
      assert.ok(port > basePort && port <= basePort + 5);
    } finally {
      await closeServer(busy);
    }
  });

  it('should reject when no free port exists in localPortRange', async function () {
    const probe = await listenOn(0);
    const basePort = (probe.address() as net.AddressInfo).port;
    await closeServer(probe);

    const busy = await listenOn(basePort);
    try {
      await assert.rejects(() => resolveLocalPort({localPortRange: [basePort, basePort]}), /No free local port found/);
    } finally {
      await closeServer(busy);
    }
  });
});
