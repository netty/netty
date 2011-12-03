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

import java.net.URI;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketSpecificationVersion;

/**
 * A HTTP client demo app
 * 
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 */
public class App {

	public static void main(String[] args) throws Exception {
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.FINE);
		Logger.getLogger("").addHandler(ch);
		Logger.getLogger("").setLevel(Level.FINE);

		runClient();
		System.exit(0);
	}

	/**
	 * Send and receive some messages using a web socket client
	 * 
	 * @throws Exception
	 */
	public static void runClient() throws Exception {

		MyCallbackHandler callbackHandler = new MyCallbackHandler();
		WebSocketClientFactory factory = new WebSocketClientFactory();

		// Connect with spec version 17 (try changing it to V10 or V00 and it will
		// still work ... fingers crossed ;-)
		WebSocketClient client = factory.newClient(new URI("ws://localhost:8080/websocket"),
				WebSocketSpecificationVersion.V17, callbackHandler);

		// Connect
    	System.out.println("WebSocket Client connecting");
    	client.connect().awaitUninterruptibly();
		Thread.sleep(200);

		// Send 10 messages and wait for responses
    	System.out.println("WebSocket Client sending message");
		for (int i = 0; i < 10; i++) {
			client.send(new TextWebSocketFrame("Message #" + i));
		}
		Thread.sleep(1000);

		// Ping
    	System.out.println("WebSocket Client sending ping");
		client.send(new PingWebSocketFrame(ChannelBuffers.copiedBuffer(new byte[] { 1, 2, 3, 4, 5, 6 })));
		Thread.sleep(1000);

		// Close
    	System.out.println("WebSocket Client sending close");
		client.send(new CloseWebSocketFrame());
		Thread.sleep(1000);

		// Disconnect
		client.disconnect();
	}

	/**
	 * Our web socket callback handler for this app
	 */
	public static class MyCallbackHandler implements WebSocketCallback {
		public boolean connected = false;
		public ArrayList<String> messagesReceived = new ArrayList<String>();

		public MyCallbackHandler() {
		}

		@Override
		public void onConnect(WebSocketClient client) {
			System.out.println("WebSocket Client connected!");
			connected = true;
		}

		@Override
		public void onDisconnect(WebSocketClient client) {
			System.out.println("WebSocket Client disconnected!");
			connected = false;
		}

		@Override
		public void onMessage(WebSocketClient client, WebSocketFrame frame) {
			if (frame instanceof TextWebSocketFrame) {
				TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
				System.out.println("WebSocket Client received message:" + textFrame.getText());
				messagesReceived.add(textFrame.getText());
			} else if (frame instanceof PongWebSocketFrame) {
				System.out.println("WebSocket Client received pong");
			} else if (frame instanceof CloseWebSocketFrame) {
				System.out.println("WebSocket Client received closing");
			}
		}

		@Override
		public void onError(Throwable t) {
			System.out.println("WebSocket Client error " + t.toString());
		}

	}

}
