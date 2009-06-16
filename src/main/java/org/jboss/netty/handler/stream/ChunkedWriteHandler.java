/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.stream;

import static org.jboss.netty.channel.Channels.*;

import java.util.Queue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.LinkedTransferQueue;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class ChunkedWriteHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(ChunkedWriteHandler.class);

    private final Queue<MessageEvent> queue =
        new LinkedTransferQueue<MessageEvent>();

    private MessageEvent currentEvent;

    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        if (!(e instanceof MessageEvent)) {
            ctx.sendDownstream(e);
            return;
        }

        queue.offer((MessageEvent) e);
        if (ctx.getChannel().isWritable()) {
            flushWriteEventQueue(ctx);
        }
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent cse = (ChannelStateEvent) e;
            if (cse.getState() == ChannelState.INTEREST_OPS &&
                ctx.getChannel().isWritable()) {
                // Continue writing when the channel becomes writable.
                flushWriteEventQueue(ctx);
            }
        }
        ctx.sendUpstream(e);
    }

    private synchronized void flushWriteEventQueue(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.getChannel();
        do {
            if (currentEvent == null) {
                currentEvent = queue.poll();
            }

            if (currentEvent == null) {
                break;
            }

            Object m = currentEvent.getMessage();
            if (m instanceof ChunkedInput) {
                ChunkedInput chunks = (ChunkedInput) m;
                Object chunk;
                boolean last;
                try {
                    chunk = chunks.readChunk();
                    last = !chunks.available();
                } catch (Throwable t) {
                    currentEvent.getFuture().setFailure(t);
                    fireExceptionCaught(ctx, t);
                    try {
                        chunks.close();
                    } catch (Throwable t2) {
                        logger.warn("Failed to close a chunked input.", t2);
                    }
                    break;
                }

                if (chunk != null) {
                    ChannelFuture writeFuture;
                    final MessageEvent currentEvent = this.currentEvent;
                    if (last) {
                        this.currentEvent = null;
                        writeFuture = currentEvent.getFuture();
                        writeFuture.addListener(new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture future)
                                    throws Exception {
                                ((ChunkedInput) currentEvent.getMessage()).close();
                            }
                        });

                    } else {
                        writeFuture = future(channel);
                        writeFuture.addListener(new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture future)
                                    throws Exception {
                                if (!future.isSuccess()) {
                                    currentEvent.getFuture().setFailure(future.getCause());
                                    ((ChunkedInput) currentEvent.getMessage()).close();
                                }
                            }
                        });
                    }

                    Channels.write(
                            ctx, writeFuture, chunk,
                            currentEvent.getRemoteAddress());
                } else {
                    currentEvent = null;
                }
            } else {
                ctx.sendDownstream(currentEvent);
                currentEvent = null;
            }
        } while (channel.isWritable());
    }
}
