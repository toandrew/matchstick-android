/*
 * Copyright (C) 2013-2015, The OpenFlint Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package tv.matchstick.flintreceiver;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Flint WebSocket
 * 
 * @author jim
 *
 */
class FlintWebSocket extends WebSocketClient {
    private static final String TAG = "FlintWebSocket";

    private FlintLogger log = new FlintLogger(TAG);

    private final FlintWebSocketListener mSocketListener;

    public FlintWebSocket(FlintWebSocketListener listener, URI serverURI) {
        super(serverURI);

        log.d("url = " + serverURI.toString());

        mSocketListener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.d("open");

        mSocketListener.onOpen("");
    }

    @Override
    public void onMessage(String message) {
        mSocketListener.onMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.e("close: " + code + "; " + reason);

        mSocketListener.onClose(reason);
    }

    @Override
    public void onError(Exception ex) {
        log.e("error");

        mSocketListener.onError(ex.toString());
    }
}
