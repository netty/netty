/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.channel.socket.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpSession;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * A channel handler that proxies messages to the servlet output stream
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
class HttpTunnelingChannelHandler extends SimpleChannelUpstreamHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HttpTunnelingChannelHandler.class);

    List<MessageEvent> awaitingEvents = new ArrayList<MessageEvent>();

    private final Lock reconnectLock = new ReentrantLock();

    private final Condition reconnectCondition = reconnectLock.newCondition();

    private final long reconnectTimeoutMillis;

    private volatile boolean connected = false;

    private final AtomicBoolean invalidated = new AtomicBoolean(false);

    private volatile ServletOutputStream outputStream;

    private final boolean stream;

    private final HttpSession session;

    public HttpTunnelingChannelHandler(
            boolean stream, HttpSession session, long reconnectTimeoutMillis) {
        this.stream = stream;
        this.session = session;
        this.reconnectTimeoutMillis = reconnectTimeoutMillis;
    }

    @Override
    public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
        if (stream) {
            boolean success = false;
            Throwable cause = null;
            byte[] b = null;
            reconnectLock.lock();
            try {
                if (outputStream == null) {
                    awaitingEvents.add(e);
                    return;
                }
                b = new byte[buffer.readableBytes()];
                buffer.readBytes(b);
                outputStream.write(b);
                outputStream.flush();
                success = true;
            } catch (Throwable t) {
                success = false;
                cause = t;
                if (awaitReconnect()) {
                    try {
                        outputStream.write(b);
                        outputStream.flush();
                        success = true;
                    } catch (Throwable t2) {
                        success = false;
                        cause = t2;
                    }
                } else {
                    if (invalidated.compareAndSet(false, true)) {
                        session.invalidate();
                    }
                    e.getChannel().close();
                }
            } finally {
                reconnectLock.unlock();
                if (success) {
                    e.getFuture().setSuccess();
                } else {
                    assert cause != null;
                    e.getFuture().setFailure(cause);
                }
            }
        } else {
            awaitingEvents.add(e);
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.warn("Unexpected exception", e.getCause());
        if (invalidated.compareAndSet(false, true)) {
            session.invalidate();
        }
        e.getChannel().close();
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (invalidated.compareAndSet(false, true)) {
            session.invalidate();
        }
    }

    synchronized List<MessageEvent> getAwaitingEvents() {
        List<MessageEvent> list = new ArrayList<MessageEvent>();
        list.addAll(awaitingEvents);
        awaitingEvents.clear();
        return list;
    }

    void setOutputStream(ServletOutputStream outputStream) {
        reconnectLock.lock();
        try {
            this.outputStream = outputStream;
            connected = true;
            for (MessageEvent awaitingEvent : awaitingEvents) {
                ChannelBuffer buffer = (ChannelBuffer) awaitingEvent.getMessage();
                byte[] b = new byte[buffer.readableBytes()];
                buffer.readBytes(b);
                try {
                    outputStream.write(b);
                    outputStream.flush();
                    awaitingEvent.getFuture().setSuccess();
                }
                catch (IOException e) {
                    awaitingEvent.getFuture().setFailure(e);
                }
            }
            reconnectCondition.signalAll();
        }
        finally {
            reconnectLock.unlock();
        }
    }

    boolean isStreaming() {
        return stream;
    }

    boolean awaitReconnect() {
        reconnectLock.lock();
        try {
            connected = false;
            reconnectCondition.await(reconnectTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // return with current state.
        } finally {
            reconnectLock.unlock();
        }
        return connected;
    }
}

