/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http.websocketx.client;

import java.net.URI;

/**
 * This is an example of a secure WebSocket client.
 * <p>
 * In order to run this example you need a compatible secure WebSocket server.
 * Therefore you can either start the secure WebSocket server from the examples
 * by running {@link io.netty.example.http.websocketx.sslserver.WebSocketSslServer}
 * or connect to an existing secure WebSocket server such as
 * <a href="http://www.websocket.org/echo.html">wss://echo.websocket.org</a>.
 * <p>
 * The client will attempt to connect to the URI passed to it as the first argument.
 * You don't have to specify any arguments if you want to connect to the example secure WebSocket server,
 * as this is the default.
 */
public final class WebSocketSslClient {
    private WebSocketSslClient() {
    }

    public static void main(String... args) throws Exception {
        URI uri;
        if (args.length > 0) {
            uri = new URI(args[0]);
        } else {
            uri = new URI("wss://localhost:8443/websocket");
        }

        new WebSocketClientRunner(uri).run();
    }
}
