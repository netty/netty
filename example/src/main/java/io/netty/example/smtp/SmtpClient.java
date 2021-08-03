/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.smtp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.smtp.DefaultSmtpRequest;
import io.netty.handler.codec.smtp.SmtpCommand;
import io.netty.handler.codec.smtp.SmtpRequest;
import io.netty.handler.codec.smtp.SmtpRequestEncoder;
import io.netty.handler.codec.smtp.SmtpResponse;
import io.netty.handler.codec.smtp.SmtpResponseDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;

import java.util.Base64;

import static io.netty.handler.codec.smtp.SmtpCommand.AUTH;
import static io.netty.handler.codec.smtp.SmtpCommand.DATA;
import static io.netty.handler.codec.smtp.SmtpCommand.EHLO;
import static io.netty.handler.codec.smtp.SmtpCommand.EMPTY;
import static io.netty.handler.codec.smtp.SmtpCommand.MAIL;
import static io.netty.handler.codec.smtp.SmtpCommand.QUIT;
import static io.netty.handler.codec.smtp.SmtpCommand.RCPT;

/**
 * A simple smtp client
 */
public class SmtpClient {

    private static final String HOST = System.getProperty("host", "127.0.0.1");
    private static final int PORT = Integer.parseInt(System.getProperty("port", "25"));

    private static final String USERNAME = "sample-user@test.com";
    private static final String PASSWORD = "sample-password";

    private static final String SENDER = "sample-sender@test.com";
    private static final String RECEIVER = "sample-receiver@test.com";

    private static final String content = "From: " + SENDER + "\r\n" +
                                          "To: " + RECEIVER + "\r\n" +
                                          "Subject: test\r\n" +
                                          "\r\n" +
                                          "This is a test message.\r\n" +
                                          ".\r\n";

    public static void main(String[] args) throws Exception {
        SmtpClientHandler handler = new SmtpClientHandler();
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new SmtpRequestEncoder());
                     p.addLast(new SmtpResponseDecoder(Integer.MAX_VALUE));
                     p.addLast(handler);
                 }
             });

            ByteBuf mailContent = Unpooled.wrappedBuffer(content.getBytes(CharsetUtil.UTF_8));

            // Start send smtp command.
            Future<SmtpResponse> f = handler.createResponseFuture(group.next())
                    .thenCompose(v -> handler.send(req(EHLO, "localhost")))
                    .thenCompose(r -> handler.send(req(AUTH, "login")))
                    .thenCompose(r -> handler.send(req(EMPTY, encode(USERNAME))))
                    .thenCompose(r -> handler.send(req(EMPTY, encode(PASSWORD))))
                    .thenCompose(r -> handler.send(req(MAIL, String.format("FROM:<%s>", SENDER))))
                    .thenCompose(r -> handler.send(req(RCPT, String.format("TO:<%s>", RECEIVER))))
                    .thenCompose(r -> handler.send(req(DATA)))
                    .thenCompose(r -> handler.sendMailContent(mailContent))
                    .thenCompose(r -> handler.send(req(QUIT)))
                    .future();

            // Start connect.
            Channel ch = b.connect(HOST, PORT).get();
            f.await();
            ch.close();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static String encode(String msg) {
        return Base64.getEncoder().encodeToString(msg.getBytes(CharsetUtil.UTF_8));
    }

    private static SmtpRequest req(SmtpCommand command, CharSequence... arguments) {
        return new DefaultSmtpRequest(command, arguments);
    }
}
