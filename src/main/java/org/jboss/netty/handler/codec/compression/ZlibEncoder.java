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
package org.jboss.netty.handler.codec.compression;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.jboss.netty.util.internal.jzlib.JZlib;
import org.jboss.netty.util.internal.jzlib.ZStream;


/**
 * Compresses a {@link ChannelBuffer} using the deflate algorithm.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2241 $, $Date: 2010-04-16 13:12:43 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.handler.codec.compression.ZlibWrapper
 */
public class ZlibEncoder extends OneToOneEncoder implements LifeCycleAwareChannelHandler {

    private static final byte[] EMPTY_ARRAY = new byte[0];

    private final ZStream z = new ZStream();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile ChannelHandlerContext ctx;

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6})
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibEncoder() {
        this(6);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibEncoder(int compressionLevel) {
        this(ZlibWrapper.ZLIB, compressionLevel);
    }

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6})
     * and the specified wrapper.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibEncoder(ZlibWrapper wrapper) {
        this(wrapper, 6);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}
     * and the specified wrapper.
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibEncoder(ZlibWrapper wrapper, int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel +
                    " (expected: 0-9)");
        }
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }

        synchronized (z) {
            int resultCode = z.deflateInit(compressionLevel, ZlibUtil.convertWrapperType(wrapper));
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "initialization failure", resultCode);
            }
        }
    }

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6})
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param dictionary  the preset dictionary
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibEncoder(byte[] dictionary) {
        this(6, dictionary);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @param dictionary  the preset dictionary
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibEncoder(int compressionLevel, byte[] dictionary) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }

        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }

        synchronized (z) {
            int resultCode;
            resultCode = z.deflateInit(compressionLevel, JZlib.W_ZLIB); // Default: ZLIB format
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "initialization failure", resultCode);
            } else {
                resultCode = z.deflateSetDictionary(dictionary, dictionary.length);
                if (resultCode != JZlib.Z_OK){
                    ZlibUtil.fail(z, "failed to set the dictionary", resultCode);
                }
            }
        }
    }

    public ChannelFuture close() {
        ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            throw new IllegalStateException("not added to a pipeline");
        }
        return finishEncode(ctx, null);
    }

    public boolean isClosed() {
        return finished.get();
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer) || finished.get()) {
            return msg;
        }

        ChannelBuffer result;
        synchronized (z) {
            try {
                // Configure input.
                ChannelBuffer uncompressed = (ChannelBuffer) msg;
                byte[] in = new byte[uncompressed.readableBytes()];
                uncompressed.readBytes(in);
                z.next_in = in;
                z.next_in_index = 0;
                z.avail_in = in.length;

                // Configure output.
                byte[] out = new byte[(int) Math.ceil(in.length * 1.001) + 12];
                z.next_out = out;
                z.next_out_index = 0;
                z.avail_out = out.length;

                // Note that Z_PARTIAL_FLUSH has been deprecated.
                int resultCode = z.deflate(JZlib.Z_SYNC_FLUSH);
                if (resultCode != JZlib.Z_OK) {
                    ZlibUtil.fail(z, "compression failure", resultCode);
                }

                if (z.next_out_index != 0) {
                    result = ctx.getChannel().getConfig().getBufferFactory().getBuffer(
                            uncompressed.order(), out, 0, z.next_out_index);
                } else {
                    result = ChannelBuffers.EMPTY_BUFFER;
                }
            } finally {
                // Deference the external references explicitly to tell the VM that
                // the allocated byte arrays are temporary so that the call stack
                // can be utilized.
                // I'm not sure if the modern VMs do this optimization though.
                z.next_in = null;
                z.next_out = null;
            }
        }

        return result;
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt)
            throws Exception {
        if (evt instanceof ChannelStateEvent) {
            ChannelStateEvent e = (ChannelStateEvent) evt;
            switch (e.getState()) {
            case OPEN:
            case CONNECTED:
            case BOUND:
                if (Boolean.FALSE.equals(e.getValue()) || e.getValue() == null) {
                    finishEncode(ctx, evt);
                    return;
                }
            }
        }

        super.handleDownstream(ctx, evt);
    }

    private ChannelFuture finishEncode(final ChannelHandlerContext ctx, final ChannelEvent evt) {
        if (!finished.compareAndSet(false, true)) {
            if (evt != null) {
                ctx.sendDownstream(evt);
            }
            return Channels.succeededFuture(ctx.getChannel());
        }

        ChannelBuffer footer;
        ChannelFuture future;
        synchronized (z) {
            try {
                // Configure input.
                z.next_in = EMPTY_ARRAY;
                z.next_in_index = 0;
                z.avail_in = 0;

                // Configure output.
                byte[] out = new byte[32]; // room for ADLER32 + ZLIB / CRC32 + GZIP header
                z.next_out = out;
                z.next_out_index = 0;
                z.avail_out = out.length;

                // Write the ADLER32 checksum (stream footer).
                int resultCode = z.deflate(JZlib.Z_FINISH);
                if (resultCode != JZlib.Z_OK && resultCode != JZlib.Z_STREAM_END) {
                    future = Channels.failedFuture(
                            ctx.getChannel(),
                            ZlibUtil.exception(z, "compression failure", resultCode));
                    footer = null;
                } else if (z.next_out_index != 0) {
                    future = Channels.future(ctx.getChannel());
                    footer =
                        ctx.getChannel().getConfig().getBufferFactory().getBuffer(
                                out, 0, z.next_out_index);
                } else {
                    // Note that we should never use a SucceededChannelFuture
                    // here just in case any downstream handler or a sink wants
                    // to notify a write error.
                    future = Channels.future(ctx.getChannel());
                    footer = ChannelBuffers.EMPTY_BUFFER;
                }
            } finally {
                z.deflateEnd();

                // Deference the external references explicitly to tell the VM that
                // the allocated byte arrays are temporary so that the call stack
                // can be utilized.
                // I'm not sure if the modern VMs do this optimization though.
                z.next_in = null;
                z.next_out = null;
            }
        }

        if (footer != null) {
            Channels.write(ctx, future, footer);
        }

        if (evt != null) {
            future.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    ctx.sendDownstream(evt);
                }
            });
        }

        return future;
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // Unused
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // Unused
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // Unused
    }
}
