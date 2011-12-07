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
package org.jboss.netty.example.sctp;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.sctp.SctpPayload;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler implementation for the echo client.  It initiates the message
 * and upon receiving echo back to the server
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 */
public class SctpClientHandler extends SimpleChannelUpstreamHandler {
    private static final Logger logger = Logger.getLogger(SctpClientHandler.class.getName());

    private final AtomicLong counter = new AtomicLong(0);

    /**
     * Creates a client-side handler.
     */
    public SctpClientHandler() {
    }

    /**
     * After connection is initialized, start the echo from client
     */
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent stateEvent) {
        stateEvent.getChannel().write(new SctpPayload(0, 0, ChannelBuffers.wrappedBuffer("SCTP ECHO".getBytes())));
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) {
        // Send back the received message to the remote peer.
        logger.log(Level.INFO, "Received " + counter.incrementAndGet() + "th message from server, sending it back.");
        messageEvent.getChannel().write(messageEvent.getMessage());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent exceptionEvent) {
        // Close the connection when an exception is raised.
        logger.log(Level.WARNING, "Unexpected exception from downstream.", exceptionEvent.getCause());
        exceptionEvent.getChannel().close();
    }
}
