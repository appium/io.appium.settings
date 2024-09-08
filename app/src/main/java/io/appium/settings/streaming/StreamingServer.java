/*
  Copyright 2012-present Appium Committers
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package io.appium.settings.streaming;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class StreamingServer extends WebSocketServer {
    public static final String ON_CONNECT = "connect";
    public static final String ON_CLOSE = "close";
    public static final String ON_ERROR = "error";
    public static final String ON_START = "start";
    private final EventEmitter<WebSocket> eventEmitter;

    public StreamingServer(int port, EventEmitter<WebSocket> eventEmitter) throws IOException {
        super(new InetSocketAddress(port));
        this.eventEmitter = eventEmitter;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        eventEmitter.emit(ON_CONNECT, conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        eventEmitter.emit(ON_CLOSE, conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {

    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        eventEmitter.emit(ON_ERROR, conn);
    }

    @Override
    public void onStart() {
        eventEmitter.emit(ON_START, null);
    }
}
