/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.oio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.FileRegion;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.internal.ObjectUtil;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.WritableByteChannel;

/**
 * Abstract base class for OIO Channels that are based on streams.
 *
 * @deprecated use NIO / EPOLL / KQUEUE transport.
 */
@Deprecated
public abstract class OioByteStreamChannel extends AbstractOioByteChannel {

    private static final InputStream CLOSED_IN = new InputStream() {
        @Override
        public int read() {
            return -1;
        }
    };

    private static final OutputStream CLOSED_OUT = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            throw new ClosedChannelException();
        }
    };

    private InputStream is;
    private OutputStream os;
    private WritableByteChannel outChannel;

    /**
     * Create a new instance
     *
     * @param parent    the parent {@link Channel} which was used to create this instance. This can be null if the
     *                  {@link} has no parent as it was created by your self.
     */
    protected OioByteStreamChannel(Channel parent) {
        super(parent);
    }

    /**
     * Activate this instance. After this call {@link #isActive()} will return {@code true}.
     */
    protected final void activate(InputStream is, OutputStream os) {
        if (this.is != null) {
            throw new IllegalStateException("input was set already");
        }
        if (this.os != null) {
            throw new IllegalStateException("output was set already");
        }
        this.is = ObjectUtil.checkNotNull(is, "is");
        this.os = ObjectUtil.checkNotNull(os, "os");
        if (readWhenInactive) {
            eventLoop().execute(readTask);
            readWhenInactive = false;
        }
    }

    @Override
    public boolean isActive() {
        InputStream is = this.is;
        if (is == null || is == CLOSED_IN) {
            return false;
        }

        OutputStream os = this.os;
        return !(os == null || os == CLOSED_OUT);
    }

    @Override
    protected int available() {
        try {
            return is.available();
        } catch (IOException ignored) {
            return 0;
        }
    }

    @Override
    protected int doReadBytes(ByteBuf buf) throws Exception {
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        allocHandle.attemptedBytesRead(Math.max(1, Math.min(available(), buf.maxWritableBytes())));
        return buf.writeBytes(is, allocHandle.attemptedBytesRead());
    }

    @Override
    protected void doWriteBytes(ByteBuf buf) throws Exception {
        OutputStream os = this.os;
        if (os == null) {
            throw new NotYetConnectedException();
        }
        buf.readBytes(os, buf.readableBytes());
    }

    @Override
    protected void doWriteFileRegion(FileRegion region) throws Exception {
        OutputStream os = this.os;
        if (os == null) {
            throw new NotYetConnectedException();
        }
        if (outChannel == null) {
            outChannel = Channels.newChannel(os);
        }

        long written = 0;
        for (;;) {
            long localWritten = region.transferTo(outChannel, written);
            if (localWritten == -1) {
                checkEOF(region);
                return;
            }
            written += localWritten;

            if (written >= region.count()) {
                return;
            }
        }
    }

    private static void checkEOF(FileRegion region) throws IOException {
        if (region.transferred() < region.count()) {
            throw new EOFException("Expected to be able to write " + region.count() + " bytes, " +
                                   "but only wrote " + region.transferred());
        }
    }

    @Override
    protected void doClose() throws Exception {
        InputStream is = this.is;
        OutputStream os = this.os;
        this.is = CLOSED_IN;
        this.os = CLOSED_OUT;

        try {
            if (is != null) {
                is.close();
            }
        } finally {
            if (os != null) {
                os.close();
            }
        }
    }
}
