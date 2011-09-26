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

package org.jboss.netty.example.http.websocketx.client;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.example.http.websocketx.server.WebSocketServerPipelineFactory;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketSpecificationVersion;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * A HTTP client demo app
 * 
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 * 
 * @version $Rev$, $Date$
 */
public class App {

	private static final ChannelGroup allChannels = new DefaultChannelGroup("App");
	private static ChannelFactory channelFactory = null;

	public static void main(String[] args) throws Exception {
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.FINE);
		Logger.getLogger("").addHandler(ch);
		Logger.getLogger("").setLevel(Level.FINE);

		startServer();

		runClient();

		stopServer();
	}

	/**
	 * Starts our web socket server
	 */
	public static void startServer() {
		// Configure the server.
		channelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool());

		ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);

		// Set up the event pipeline factory.
		bootstrap.setPipelineFactory(new WebSocketServerPipelineFactory());

		// Bind and start to accept incoming connections.
		Channel channel = bootstrap.bind(new InetSocketAddress(8080));
		allChannels.add(channel);

		System.out
				.println("Web Socket Server started on 8080. Open your browser and navigate to http://localhost:8080/");
	}

	/**
	 * Send and receive some messages using a web socket client
	 * 
	 * @throws Exception
	 */
	public static void runClient() throws Exception {

		MyCallbackHandler callbackHandler = new MyCallbackHandler();
		WebSocketClientFactory factory = new WebSocketClientFactory();

		// Connect with spec version 10 (try changing it to V00 and it will
		// still work ... fingers crossed ;-)
		WebSocketClient client = factory.newClient(new URI("ws://localhost:8080/websocket"),
				WebSocketSpecificationVersion.V10, callbackHandler);

		// Connect
		client.connect().awaitUninterruptibly();
		Thread.sleep(200);

		// Send 10 messages and wait for responses
		for (int i = 0; i < 10; i++) {
			client.send(new TextWebSocketFrame("Message #" + i));
		}
		Thread.sleep(1000);

		// Close - this throws ClosedChannelException. Not sure why. Just as
		// easy to just disconnect.
		client.send(new PingWebSocketFrame(ChannelBuffers.copiedBuffer(new byte[] { 1, 2, 3, 4, 5, 6 })));
		client.send(new CloseWebSocketFrame());
		Thread.sleep(200);

		// Disconnect
		client.disconnect();
	}

	/**
	 * Stops the server
	 */
	public static void stopServer() {
		ChannelGroupFuture future = allChannels.close();
		future.awaitUninterruptibly();

		channelFactory.releaseExternalResources();
		channelFactory = null;

	}

	/**
	 * Our web socket callback handler for this app
	 */
	public static class MyCallbackHandler implements WebSocketCallback {

		private static final InternalLogger logger = InternalLoggerFactory.getInstance(MyCallbackHandler.class);

		public boolean connected = false;
		public ArrayList<String> messagesReceived = new ArrayList<String>();

		public MyCallbackHandler() {
			return;
		}

		@Override
		public void onConnect(WebSocketClient client) {
			logger.debug("WebSocket Client connected!");
			connected = true;
		}

		@Override
		public void onDisconnect(WebSocketClient client) {
			logger.debug("WebSocket Client disconnected!");
			connected = false;
		}

		@Override
		public void onMessage(WebSocketClient client, WebSocketFrame frame) {
			if (frame instanceof TextWebSocketFrame) {
				TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
				logger.debug("WebSocket Client Received Message:" + textFrame.getText());
				messagesReceived.add(textFrame.getText());
			} else if (frame instanceof PongWebSocketFrame) {
				logger.debug("WebSocket Client ping/pong");
			} else if (frame instanceof CloseWebSocketFrame) {
				logger.debug("WebSocket Client closing");
			}
		}

		@Override
		public void onError(Throwable t) {
			logger.error("WebSocket Client error", t);
		}

	}

}
