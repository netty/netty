/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.oio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.WritableByteChannel;

/**
 * Abstract base class for OIO Channels that are based on streams.
 */
public abstract class OioByteStreamChannel extends AbstractOioByteChannel {

    private InputStream is;
    private OutputStream os;
    private WritableByteChannel outChannel;

    /**
     * Create a new instance
     *
     * @param parent    the parent {@link Channel} which was used to create this instance. This can be null if the
     *                  {@link} has no parent as it was created by your self.
     * @param id        the id which should be used for this instance or {@code null} if a new one should be generated
     */
    protected OioByteStreamChannel(Channel parent, Integer id) {
        super(parent, id);
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
        if (is == null) {
            throw new NullPointerException("is");
        }
        if (os == null) {
            throw new NullPointerException("os");
        }
        this.is = is;
        this.os = os;
    }

    @Override
    public boolean isActive() {
        return is != null && os != null;
    }

    @Override
    protected int available() {
        try {
            return is.available();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    protected int doReadBytes(ByteBuf buf) throws Exception {
        int length = Math.max(1, Math.min(available(), buf.maxWritableBytes()));
        return buf.writeBytes(is, length);
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
    protected void doFlushFileRegion(FileRegion region, ChannelPromise promise) throws Exception {
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
                checkEOF(region, written);
                region.release();
                promise.setSuccess();
                return;
            }
            written += localWritten;
            if (promise instanceof ChannelProgressivePromise) {
                final ChannelProgressivePromise pp = (ChannelProgressivePromise) promise;
                pp.setProgress(written, region.count());
            }
            if (written >= region.count()) {
                promise.setSuccess();
                return;
            }
        }
    }

    @Override
    protected void doClose() throws Exception {
        IOException ex = null;

        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            ex = e;
        }

        try {
            if (os != null) {
                os.close();
            }
        } catch (IOException e) {
            ex = e;
        }

        is = null;
        os = null;

        if (ex != null) {
            throw ex;
        }
    }
}
