//The MIT License
//
//Copyright (c) 2009 Carl Bystršm
//
//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in
//all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//THE SOFTWARE.
package io.netty.example.http.websocketx.client;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.Executors;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.socket.nio.NioClientSocketChannelFactory;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

public class WebSocketClient {

    private final URI uri;
    
    public WebSocketClient(URI uri) {
        this.uri = uri;
    }
    
    public void run() throws Exception {
        ClientBootstrap bootstrap =
                new ClientBootstrap(
                        new NioClientSocketChannelFactory(
                                Executors.newCachedThreadPool(),
                                Executors.newCachedThreadPool()));

        String protocol = uri.getScheme();
        if (!protocol.equals("ws")) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }

        HashMap<String, String> customHeaders = new HashMap<String, String>();
        customHeaders.put("MyHeader", "MyValue");

        // Connect with V13 (RFC 6455). You can change it to V08 or V00.
        // If you change it to V00, ping is not supported and remember to change
        // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
        final WebSocketClientHandshaker handshaker =
                new WebSocketClientHandshakerFactory().newHandshaker(
                        uri, WebSocketVersion.V13, null, false, customHeaders);
        
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();

                // If you wish to support HyBi V00, you need to use
                // WebSocketHttpResponseDecoder instead for
                // HttpResponseDecoder.
                pipeline.addLast("decoder", new HttpResponseDecoder());

                pipeline.addLast("encoder", new HttpRequestEncoder());
                pipeline.addLast("ws-handler", new WebSocketClientHandler(handshaker));
                return pipeline;
            }
        });

        // Connect
        System.out.println("WebSocket Client connecting");
        ChannelFuture future =
                bootstrap.connect(
                        new InetSocketAddress(uri.getHost(), uri.getPort()));
        future.awaitUninterruptibly();
        
        Channel ch = future.getChannel();

        handshaker.handshake(ch).awaitUninterruptibly();
        
        // Send 10 messages and wait for responses
        System.out.println("WebSocket Client sending message");
        for (int i = 0; i < 10; i++) {
            ch.write(new TextWebSocketFrame("Message #" + i));
        }

        // Ping
        System.out.println("WebSocket Client sending ping");
        ch.write(new PingWebSocketFrame(ChannelBuffers.copiedBuffer(new byte[]{1, 2, 3, 4, 5, 6})));

        // Close
        System.out.println("WebSocket Client sending close");
        ch.write(new CloseWebSocketFrame());

        // WebSocketClientHandler will close the connection when the server
        // responds to the CloseWebSocketFrame.
        ch.getCloseFuture().awaitUninterruptibly();

        bootstrap.releaseExternalResources();
    }

    public static void main(String[] args) throws Exception {
        URI uri;
        if (args.length > 0) {
            uri = new URI(args[0]);
        } else {
            uri = new URI("ws://localhost:8080/websocket");
        }
        new WebSocketClient(uri).run();
    }
}
