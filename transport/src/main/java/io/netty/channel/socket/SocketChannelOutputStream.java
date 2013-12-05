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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelException;
import java.io.IOException;
import java.io.OutputStream;

final class SocketChannelOutputStream extends OutputStream {

    private final SocketChannel ch;

    public SocketChannelOutputStream(SocketChannel ch) {
        this.ch = ch;
    }

    @Override
    public void close() throws IOException {
        try {
            ch.shutdownOutput().syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SocketChannelOutputStream && ch.equals(((SocketChannelOutputStream) obj).ch);
    }

    @Override
    public void flush() throws IOException {
        try {
            ch.flush().syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public int hashCode() {
        return ch.hashCode();
    }

    @Override
    public String toString() {
        return ch.toString();
    }

    @Override
    public void write(byte[] b) throws IOException {
        try {
            ch.write(Unpooled.wrappedBuffer(b)).syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void write(int b) throws IOException {
        try {
            ch.write(Unpooled.wrappedBuffer(new byte[]{(byte) b})).syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            ch.write(Unpooled.wrappedBuffer(b, off, len)).syncUninterruptibly();
        } catch (ChannelException ex) {
            throw new IOException(ex);
        }
    }
}
