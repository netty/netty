/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http.websocketx.server;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * A HTTP server which serves Web Socket requests at:
 * 
 * http://localhost:8080/websocket
 * 
 * Open your browser at http://localhost:8080/, then the demo page will be
 * loaded and a Web Socket connection will be made automatically.
 * 
 * This server illustrates support for the different web socket specification
 * versions and will work with:
 * 
 * <ul>
 * <li>Safari 5+ (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>
 * <li>Chrome 6-13 (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>
 * <li>Chrome 14+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * <li>
 * <li>Firefox 7+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * <li>
 * </ul>
 * 
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 */
public class WebSocketServer {
	public static void main(String[] args) {
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.FINE);
		Logger.getLogger("").addHandler(ch);
		Logger.getLogger("").setLevel(Level.FINE);
		
		// Configure the server.
		ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
				Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

		// Set up the event pipeline factory.
		bootstrap.setPipelineFactory(new WebSocketServerPipelineFactory());

		// Bind and start to accept incoming connections.
		bootstrap.bind(new InetSocketAddress(8080));
		
		System.out.println("Web Socket Server started on 8080. Open your browser and navigate to http://localhost:8080/");
	}
}
