/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.jboss.netty.example.http.websocketx.html5;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.example.http.websocketx.server.WebSocketServerPipelineFactory;

/**
 * A WebSocket Server that respondes to requests at:
 *
 * <pre>
 * http://localhost:8080/websocket
 * </pre>
 *
 * The example differs from many of the other examples in Netty in that is does
 * not have an acomponying client. Instead a html page is provided that
 * interacts with this server. <br>
 * Open up the following file a web browser that supports WebSocket's:
 *
 * <pre>
 * netty/src/test/resources/websocketx/html5/websocket.html
 * </pre>
 *
 * The html page is very simple were you simply enter some text and the server
 * will echo the same text back, but in uppercase. You, also see status messages
 * in the "Response From Server" area when client has connected, disconnected
 * etc.
 *
 */
public class WebSocketServer {

    private final int port;

    public WebSocketServer(int port) {
        this.port = port;
    }

    public void run() {
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new WebSocketServerPipelineFactory());

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));

        System.out.println("Web socket server started at port " + port + '.');
    }

    public static void main(String[] args) {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8080;
        }
        new WebSocketServer(port).run();
    }

}
