/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

public final class SocketChannelAdapter extends Socket {

    private final SocketChannel ch;
    private SocketChannelOutputStream out;

    private SocketChannelAdapter(SocketChannel ch) {
        this.ch = ch;
    }

    public static SocketChannelAdapter adapt(SocketChannel ch) {
        return new SocketChannelAdapter(ch);
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        try {
            ch.bind(bindpoint).syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            ch.close().syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        try {
            ch.connect(endpoint).syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        try {
            ch.config().setConnectTimeoutMillis(timeout);
            ch.connect(endpoint).syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SocketChannelAdapter && ch.equals(((SocketChannelAdapter) obj).ch);
    }

    @Override
    public java.nio.channels.SocketChannel getChannel() {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public InetAddress getInetAddress() {
        return ch.remoteAddress().getAddress();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return ch.config().getOption(ChannelOption.SO_KEEPALIVE);
    }

    @Override
    public InetAddress getLocalAddress() {
        return ch.localAddress().getAddress();
    }

    @Override
    public int getLocalPort() {
        return ch.localAddress().getPort();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return ch.localAddress();
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return (out == null) ? out = new SocketChannelOutputStream(ch) : out;
    }

    @Override
    public int getPort() {
        return ch.remoteAddress().getPort();
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        return ch.config().getOption(ChannelOption.SO_RCVBUF);
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return ch.remoteAddress();
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return ch.config().getOption(ChannelOption.SO_REUSEADDR);
    }

    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        return ch.config().getOption(ChannelOption.SO_SNDBUF);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return ch.config().getOption(ChannelOption.SO_LINGER);
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return ch.config().getOption(ChannelOption.TCP_NODELAY);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return ch.config().getOption(ChannelOption.IP_TOS);
    }

    @Override
    public int hashCode() {
        return ch.hashCode();
    }

    @Override
    public boolean isBound() {
        return ch.localAddress() != null;
    }

    @Override
    public boolean isClosed() {
        return !ch.isOpen();
    }

    @Override
    public boolean isConnected() {
        return ch.isActive();
    }

    @Override
    public boolean isInputShutdown() {
        return ch.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return ch.isOutputShutdown();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        try {
            ch.config().setOption(ChannelOption.SO_KEEPALIVE, on);
        } catch (ChannelException ex) {
            SocketException up = new SocketException();
            up.initCause(ex);
            throw up;
        }
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        try {
            ch.config().setOption(ChannelOption.SO_RCVBUF, size);
        } catch (ChannelException ex) {
            SocketException up = new SocketException();
            up.initCause(ex);
            throw up;
        }
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        try {
            ch.config().setOption(ChannelOption.SO_REUSEADDR, on);
        } catch (ChannelException ex) {
            SocketException up = new SocketException();
            up.initCause(ex);
            throw up;
        }
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        try {
            ch.config().setOption(ChannelOption.SO_SNDBUF, size);
        } catch (ChannelException ex) {
            SocketException up = new SocketException();
            up.initCause(ex);
            throw up;
        }
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        try {
            ch.config().setOption(ChannelOption.SO_LINGER, linger);
        } catch (ChannelException ex) {
            SocketException up = new SocketException();
            up.initCause(ex);
            throw up;
        }
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        try {
            ch.config().setOption(ChannelOption.TCP_NODELAY, on);
        } catch (ChannelException ex) {
            SocketException up = new SocketException();
            up.initCause(ex);
            throw up;
        }
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        try {
            ch.config().setOption(ChannelOption.IP_TOS, tc);
        } catch (ChannelException ex) {
            SocketException up = new SocketException();
            up.initCause(ex);
            throw up;
        }
    }

    @Override
    public void shutdownInput() throws IOException {
        throw new UnsupportedOperationException("Operation not supported on Channel wrapper.");
    }

    @Override
    public void shutdownOutput() throws IOException {
        try {
            ch.shutdownOutput().syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public String toString() {
        return ch.toString();
    }
}
