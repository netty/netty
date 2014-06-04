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
package io.netty.example.stomp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.stomp.FullStompFrame;
import io.netty.handler.codec.stomp.DefaultFullStompFrame;
import io.netty.handler.codec.stomp.StompCommand;
import io.netty.handler.codec.stomp.StompHeaders;
import io.netty.handler.codec.stomp.StompEncoder;
import io.netty.handler.codec.stomp.StompDecoder;
import io.netty.handler.codec.stomp.StompAggregator;


/**
 * very simple stomp client implementation example, requires running stomp server to actually work
 * uses default username/password and destination values from hornetq message broker
 */
public class StompClient implements  StompFrameListener {
    public static final String DEAFULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 61613;
    public static final String DEFAULT_USERNAME = "guest";
    public static final String DEFAULT_PASSWORD = "guest";
    private static final String EXAMPLE_TOPIC = "jms.topic.exampleTopic";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private ClientState state = ClientState.CONNECTING;
    private Channel ch;

    public static void main(String[] args) throws Exception {
        String host;
        int port;
        String username;
        String password;
        if (args.length == 0) {
            host = DEAFULT_HOST;
            port = DEFAULT_PORT;
            username = DEFAULT_USERNAME;
            password = DEFAULT_PASSWORD;
        } else if (args.length == 4) {
            host = args[0];
            port = Integer.parseInt(args[1]);
            username = args[2];
            password = args[3];
        } else {
            System.err.println("Usage: " + StompClient.class.getSimpleName() + " <host> <port> <username> <password>");
            return;
        }
        StompClient stompClient = new StompClient(host, port, username, password);
        stompClient.run();
    }

    public StompClient(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public void run() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        final StompClient that = this;
        b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new StompDecoder());
                pipeline.addLast("encoder", new StompEncoder());
                pipeline.addLast("aggregator", new StompAggregator(1048576));
                pipeline.addLast("handler", new StompClientHandler(that));
            }
        });
        b.remoteAddress(host, port);

        this.ch = b.connect().sync().channel();

        FullStompFrame connFrame = new DefaultFullStompFrame(StompCommand.CONNECT);
        connFrame.headers().set(StompHeaders.ACCEPT_VERSION, "1.2");
        connFrame.headers().set(StompHeaders.HOST, host);
        connFrame.headers().set(StompHeaders.LOGIN, username);
        connFrame.headers().set(StompHeaders.PASSCODE, password);
        ch.writeAndFlush(connFrame).sync();
    }

    @Override
    public void onFrame(FullStompFrame frame) {
        String subscrReceiptId = "001";
        String disconReceiptId = "002";
        try {
            switch (frame.command()) {
                case CONNECTED:
                    FullStompFrame subscribeFrame = new DefaultFullStompFrame(StompCommand.SUBSCRIBE);
                    subscribeFrame.headers().set(StompHeaders.DESTINATION, EXAMPLE_TOPIC);
                    subscribeFrame.headers().set(StompHeaders.RECEIPT, subscrReceiptId);
                    subscribeFrame.headers().set(StompHeaders.ID, "1");
                    System.out.println("connected, sending subscribe frame: " + subscribeFrame);
                    state = ClientState.CONNECTED;
                    ch.writeAndFlush(subscribeFrame);
                    break;
                case RECEIPT:
                    String receiptHeader = frame.headers().get(StompHeaders.RECEIPT_ID);
                    if (state == ClientState.CONNECTED && receiptHeader.equals(subscrReceiptId)) {
                        FullStompFrame msgFrame = new DefaultFullStompFrame(StompCommand.SEND);
                        msgFrame.headers().set(StompHeaders.DESTINATION, EXAMPLE_TOPIC);
                        msgFrame.content().writeBytes("some payload".getBytes());
                        System.out.println("subscribed, sending message frame: " + msgFrame);
                        state = ClientState.SUBSCRIBED;
                        ch.writeAndFlush(msgFrame);
                    } else if (state == ClientState.DISCONNECTING && receiptHeader.equals(disconReceiptId)) {
                        System.out.println("disconnected, exiting..");
                        System.exit(0);
                    } else {
                        throw new IllegalStateException("received: " + frame + ", while internal state is " + state);
                    }
                    break;
                case MESSAGE:
                    if (state == ClientState.SUBSCRIBED) {
                        System.out.println("received frame: " + frame);
                        FullStompFrame disconnFrame = new DefaultFullStompFrame(StompCommand.DISCONNECT);
                        disconnFrame.headers().set(StompHeaders.RECEIPT, disconReceiptId);
                        System.out.println("sending disconnect frame: " + disconnFrame);
                        state = ClientState.DISCONNECTING;
                        ch.writeAndFlush(disconnFrame);
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    enum ClientState {
        CONNECTING,
        CONNECTED,
        SUBSCRIBED,
        DISCONNECTING
    }
}
