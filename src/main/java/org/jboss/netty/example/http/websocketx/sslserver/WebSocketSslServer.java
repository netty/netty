/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.http.websocketx.sslserver;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * A HTTP server which serves Web Socket requests at:
 * 
 * https://localhost:8081/websocket
 * 
 * Open your browser at https://localhost:8081/, then the demo page will be loaded and a Web Socket connection will be
 * made automatically.
 * 
 * This server illustrates support for the different web socket specification versions and will work with:
 * 
 * <ul>
 * <li>Safari 5+ (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>Chrome 6-13 (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>Chrome 14+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * <li>Chrome 16+ (RFC 6455 aka draft-ietf-hybi-thewebsocketprotocol-17)
 * <li>Firefox 7+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * </ul>
 */
public class WebSocketSslServer {
    
    private final int port;

    public WebSocketSslServer(int port) {
        this.port = port;
    }

    public void run() {
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new WebSocketSslServerPipelineFactory());

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));

        System.out.println("Web socket server started at port " + port + '.');
        System.out.println("Open your browser and navigate to https://localhost:" + port + '/');
    }

    public static void main(String[] args) {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8443;
        }

        String keyStoreFilePath = System.getProperty("keystore.file.path");
        if (keyStoreFilePath == null || keyStoreFilePath.length() == 0) {
            System.out.println("ERROR: System property keystore.file.path not set. Exiting now!");
            System.exit(1);
        }

        String keyStoreFilePassword = System.getProperty("keystore.file.password");
        if (keyStoreFilePassword == null || keyStoreFilePassword.length() == 0) {
            System.out.println("ERROR: System property keystore.file.password not set. Exiting now!");
            System.exit(1);
        }
        
        new WebSocketSslServer(port).run();
    }
}
