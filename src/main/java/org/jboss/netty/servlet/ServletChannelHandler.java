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
package org.jboss.netty.servlet;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A channel handler taht proxies messages to the servlet output stream
 *
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
@ChannelPipelineCoverage("one")
class ServletChannelHandler extends SimpleChannelHandler {
    List<MessageEvent> awaitingEvents = new ArrayList<MessageEvent>();

    private Lock reconnectLock = new ReentrantLock();

    private Condition reconnectCondition = reconnectLock.newCondition();

    private long reconnectTimeout;

    boolean connected = false;

    AtomicBoolean invalidated = new AtomicBoolean(false);

    private ServletOutputStream outputStream;

    final boolean stream;

    private final HttpSession session;

    public ServletChannelHandler(boolean stream, HttpSession session, long reconnectTimeout) {
        this.stream = stream;
        this.session = session;
        this.reconnectTimeout = reconnectTimeout;
    }

    public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {

        ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
        if (stream) {
            reconnectLock.lock();
            if (outputStream == null) {
                awaitingEvents.add(e);
                return;
            }
            byte[] b = new byte[buffer.readableBytes()];
            buffer.readBytes(b);
            try {
                outputStream.write(b);
                outputStream.flush();
                e.getFuture().setSuccess();
            }
            catch (IOException e1) {
                connected = false;
                reconnectCondition.await(reconnectTimeout, TimeUnit.MILLISECONDS);
                if (connected) {
                    outputStream.write(b);
                    outputStream.flush();
                    e.getFuture().setSuccess();
                }
                else {
                    e.getFuture().setFailure(e1);
                    if (invalidated.compareAndSet(false, true)) {
                        session.invalidate();
                    }
                    e.getChannel().close();
                }
            }
            finally {
                reconnectLock.unlock();
            }
        }

        else {
            awaitingEvents.add(e);
        }

    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (invalidated.compareAndSet(false, true)) {
            session.invalidate();
        }
        e.getChannel().close();
    }

    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (invalidated.compareAndSet(false, true)) {
            session.invalidate();
        }
    }

    public synchronized List<MessageEvent> getAwaitingEvents() {
        List<MessageEvent> list = new ArrayList<MessageEvent>();
        list.addAll(awaitingEvents);
        awaitingEvents.clear();
        return list;
    }

    public void setOutputStream(ServletOutputStream outputStream) {
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

    public boolean isStreaming() {
        return stream;
    }

    public ServletOutputStream getOutputStream() {
        return outputStream;
    }

    public boolean awaitReconnect() {
        reconnectLock.lock();
        connected = false;
        try {
            reconnectCondition.await(reconnectTimeout, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            return connected;
        }
        finally {
            reconnectLock.unlock();
        }
        return connected;
    }
}

