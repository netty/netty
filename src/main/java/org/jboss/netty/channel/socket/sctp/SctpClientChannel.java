/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.channel.socket.sctp;

import com.sun.nio.sctp.SctpChannel;
import org.jboss.netty.channel.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import java.io.IOException;

import static org.jboss.netty.channel.Channels.fireChannelOpen;

/**
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 *
 * @version $Rev$, $Date$
 *
 */
final class SctpClientChannel extends SctpChannelImpl {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SctpClientChannel.class);

    private static SctpChannel newSocket() {
        SctpChannel underlayingChannel;
        try {
            underlayingChannel = SctpChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }

        boolean success = false;
        try {
            underlayingChannel.configureBlocking(false);
            success = true;
        } catch (IOException e) {
            throw new ChannelException("Failed to enter non-blocking mode.", e);
        } finally {
            if (!success) {
                try {
                    underlayingChannel.close();
                } catch (IOException e) {
                    logger.warn(
                            "Failed to close a partially initialized socket.",
                            e);
                }
            }
        }

        return underlayingChannel;
    }

    volatile ChannelFuture connectFuture;
    volatile boolean boundManually;

    // Does not need to be volatile as it's accessed by only one thread.
    long connectDeadlineNanos;

    SctpClientChannel(
            ChannelFactory factory, ChannelPipeline pipeline,
            ChannelSink sink, SctpWorker worker) {

        super(null, factory, pipeline, sink, newSocket(), worker);
        fireChannelOpen(this);
    }
}
