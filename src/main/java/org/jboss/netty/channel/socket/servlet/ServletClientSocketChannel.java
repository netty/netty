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
package org.jboss.netty.channel.socket.servlet;

import static org.jboss.netty.channel.Channels.*;
import static org.jboss.netty.channel.socket.servlet.ServletClientSocketPipelineSink.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.SocketChannelConfig;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
class ServletClientSocketChannel extends AbstractChannel
      implements org.jboss.netty.channel.socket.SocketChannel {

    private final Lock lock = new ReentrantLock();

    private final Object writeLock = new Object();

    private Socket socket;

    private ServletSocketChannelConfig config;

    volatile Thread workerThread;

    private volatile PushbackInputStream in;

    private volatile OutputStream out;

    private String sessionId;

    private boolean closed = false;

    private final URL url;

    ServletClientSocketChannel(
          ChannelFactory factory,
          ChannelPipeline pipeline,
          ChannelSink sink, URL url) {

        super(null, factory, pipeline, sink);
        this.url = url;
        socket = new Socket();
        config = new ServletSocketChannelConfig(socket);
        fireChannelOpen(this);
    }

    public SocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    public boolean isBound() {
        return isOpen() && socket.isBound();
    }

    public boolean isConnected() {
        return isOpen() && socket.isConnected();
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

    PushbackInputStream getInputStream() {
        return in;
    }


    OutputStream getOutputStream() {
        return out;
    }

    void connectAndSendHeaders(boolean reconnect, SocketAddress remoteAddress) throws IOException {
        if (reconnect) {
            System.out.println("reconnecting");
            socket.close();
            socket = new Socket();
            config = config.copyConfig(socket);
        }
        socket.connect(
              remoteAddress, getConfig().getConnectTimeoutMillis());

        // Obtain I/O stream.
        in = new PushbackInputStream(socket.getInputStream(), 1);
        out = socket.getOutputStream();
        //write and read headers
        StringBuilder builder = new StringBuilder();
        builder.append("POST ").append(url.toExternalForm()).append(" HTTP/1.1").append(LINE_TERMINATOR).
              append("HOST: ").append(url.getHost()).append(":").append(url.getPort()).append(LINE_TERMINATOR).
              append("Content-Type: application/octet-stream").append(LINE_TERMINATOR).append("Transfer-Encoding: chunked").
              append(LINE_TERMINATOR).append("Content-Transfer-Encoding: Binary").append(LINE_TERMINATOR).append("Connection: Keep-Alive").
              append(LINE_TERMINATOR);
        if (reconnect) {
            builder.append("Cookie: JSESSIONID=").append(sessionId).append(LINE_TERMINATOR);
        }
        builder.append(LINE_TERMINATOR);
        String msg = builder.toString();
        socket.getOutputStream().write(msg.getBytes("ASCII7"));
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {

            if (!reconnect) {
                if (line.contains("Set-Cookie")) {
                    int start = line.indexOf("JSESSIONID=") + 11;
                    int end = line.indexOf(";", start);
                    sessionId = line.substring(start, end);
                }
            }
            if (line.equals(LINE_TERMINATOR) || line.equals("")) {
                break;
            }
        }
    }

    public void sendChunk(ChannelBuffer a) throws IOException {
        int size = a.readableBytes();
        String hex = Integer.toHexString(size) + LINE_TERMINATOR;

        try {
            synchronized (writeLock) {
                out.write(hex.getBytes());
                a.getBytes(a.readerIndex(), out, a.readableBytes());
                out.write(LINE_TERMINATOR.getBytes());
            }
        }
        catch (SocketException e) {
            if (closed) {
                throw e;
            }
            if (lock.tryLock()) {
                try {
                    connectAndSendHeaders(true, getRemoteAddress());
                }
                finally {
                    lock.unlock();
                }
            }
            else {
                try {
                    lock.lock();
                }
                finally {
                    lock.unlock();
                }
            }
        }
    }

    public byte[] receiveChunk() throws IOException {
        byte[] buf;

        try {
            buf = read();
        }
        catch (SocketException e) {
            if (closed) {
                throw e;
            }
            if (lock.tryLock()) {
                try {
                    connectAndSendHeaders(true, socket.getRemoteSocketAddress());
                }
                finally {
                    lock.unlock();
                }
            }
            else {
                try {
                    lock.lock();
                }
                finally {
                    lock.unlock();
                }
            }
            buf = read();
        }
        return buf;
    }

    private byte[] read() throws IOException {
        //
        byte[] buf;
        StringBuffer hex = new StringBuffer();
        int b;
        while ((b = in.read()) != -1) {
            if (b == 13) {
                int end = in.read();
                if (end != 10) {
                    in.unread(end);
                }
                break;
            }
            hex.append((char) b);
        }
        int bytesToRead = Integer.parseInt(hex.toString(), 16);

        buf = new byte[bytesToRead];

        if (in.available() >= bytesToRead) {
            in.read(buf, 0, bytesToRead);
        }
        else {
            int readBytes = 0;
            do {
                readBytes += in.read(buf, readBytes, bytesToRead - readBytes);
            }
            while (bytesToRead != readBytes);
        }
        int end = in.read();
        if (end != 13) {
            in.unread(end);
        }
        else {
            end = in.read();
            if (end != 10) {
                in.unread(end);
            }
        }
        return buf;
    }

    public void closeSocket() throws IOException {
       setClosed();
       closed = true;
        socket.close();
    }

    public void bindSocket(SocketAddress localAddress) throws IOException {
        socket.bind(localAddress);
    }
}