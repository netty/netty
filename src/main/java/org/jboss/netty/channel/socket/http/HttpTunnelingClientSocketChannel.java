/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.channel.socket.http;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.util.LinkedTransferQueue;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
class HttpTunnelingClientSocketChannel extends AbstractChannel
        implements org.jboss.netty.channel.socket.SocketChannel {

    private final Lock reconnectLock = new ReentrantLock();

    volatile boolean awaitingInitialResponse = true;

    private final Object writeLock = new Object();

    volatile Thread workerThread;

    String sessionId;

    boolean closed = false;

    LinkedTransferQueue<byte[]> messages = new LinkedTransferQueue<byte[]>();

    private final ClientSocketChannelFactory clientSocketChannelFactory;

    SocketChannel channel;

    private final DelimiterBasedFrameDecoder handler = new DelimiterBasedFrameDecoder(8092, ChannelBuffers.wrappedBuffer(new byte[] { '\r', '\n' }));

    private final HttpTunnelingClientSocketChannel.ServletChannelHandler servletHandler = new ServletChannelHandler();

    private HttpTunnelAddress remoteAddress;

    HttpTunnelingClientSocketChannel(
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink, ClientSocketChannelFactory clientSocketChannelFactory) {

        super(null, factory, pipeline, sink);
        this.clientSocketChannelFactory = clientSocketChannelFactory;

        DefaultChannelPipeline channelPipeline = new DefaultChannelPipeline();
        channelPipeline.addLast("DelimiterBasedFrameDecoder", handler);
        channelPipeline.addLast("servletHandler", servletHandler);
        channel = clientSocketChannelFactory.newChannel(channelPipeline);

        fireChannelOpen(this);
    }

    public SocketChannelConfig getConfig() {
        return channel.getConfig();
    }

    public InetSocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return channel.getRemoteAddress();
    }

    public boolean isBound() {
        return channel.isOpen();
    }

    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    @Override
    protected void setInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        }
        else {
            return getUnsupportedOperationFuture();
        }
    }

    void connectAndSendHeaders(boolean reconnect, HttpTunnelAddress remoteAddress) throws IOException {
        this.remoteAddress = remoteAddress;
        URI url = remoteAddress.getUri();
        if (reconnect) {
            DefaultChannelPipeline channelPipeline = new DefaultChannelPipeline();
            channelPipeline.addLast("DelimiterBasedFrameDecoder", handler);
            channelPipeline.addLast("servletHandler", servletHandler);
            channel = clientSocketChannelFactory.newChannel(channelPipeline);
        }
        SocketAddress connectAddress = new InetSocketAddress(url.getHost(), url.getPort());
        channel.connect(connectAddress);
        StringBuilder builder = new StringBuilder();
        builder.append("POST ").append(url.getRawPath()).append(" HTTP/1.1").append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR).
                append("HOST: ").append(url.getHost()).append(":").append(url.getPort()).append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR).
                append("Content-Type: application/octet-stream").append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR).append("Transfer-Encoding: chunked").
                append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR).append("Content-Transfer-Encoding: Binary").append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR).append("Connection: Keep-Alive").
                append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR);
        if (reconnect) {
            builder.append("Cookie: JSESSIONID=").append(sessionId).append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR);
        }
        builder.append(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR);
        String msg = builder.toString();
        channel.write(ChannelBuffers.wrappedBuffer(msg.getBytes("ASCII7")));
    }

    public void sendChunk(ChannelBuffer a) {
        int size = a.readableBytes();
        String hex = Integer.toHexString(size) + HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR;

        // try {
        synchronized (writeLock) {
            a.writeBytes(HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR.getBytes());
            channel.write(ChannelBuffers.wrappedBuffer(hex.getBytes()));
            channel.write(a).awaitUninterruptibly();
            //channel.write(ChannelBuffers.wrappedBuffer(LINE_TERMINATOR.getBytes()));
        }
    }

    public byte[] receiveChunk() {
        byte[] buf = null;
        try {
            buf = messages.take();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        return buf;
    }

    void reconnect() throws Exception {
        if (closed) {
            throw new IllegalStateException("channel closed");
        }
        if (reconnectLock.tryLock()) {
            try {
                awaitingInitialResponse = true;

                connectAndSendHeaders(true, remoteAddress);
            } finally {
                reconnectLock.unlock();
            }
        } else {
            try {
                reconnectLock.lock();
            } finally {
                reconnectLock.unlock();
            }
        }
    }

    public void closeSocket() {
        setClosed();
        closed = true;
        channel.close();
    }

    public void bindSocket(SocketAddress localAddress) {
        channel.bind(localAddress);
    }

    @ChannelPipelineCoverage("one")
    class ServletChannelHandler extends SimpleChannelHandler {
        int nextChunkSize = -1;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            ChannelBuffer buf = (ChannelBuffer) e.getMessage();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(0, bytes);
            if (awaitingInitialResponse) {
                String line = new String(bytes);
                if (line.contains("Set-Cookie")) {
                    int start = line.indexOf("JSESSIONID=") + 11;
                    int end = line.indexOf(";", start);
                    sessionId = line.substring(start, end);
                }
                else if (line.equals("")) {
                    awaitingInitialResponse = false;
                }
            }
            else {
                if(nextChunkSize == -1) {
                    String hex = new String(bytes);
                    nextChunkSize = Integer.parseInt(hex, 16);
                    if(nextChunkSize == 0) {
                        if(!closed) {
                            nextChunkSize = -1;
                            awaitingInitialResponse = true;
                            reconnect();
                        }
                    }
                }
                else {
                    messages.put(bytes);
                    nextChunkSize = -1;
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            channel.close();
        }
    }
}