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
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;
import com.jcraft.jzlib.ZStreamException;

/**
 * Compresses a {@link ChannelBuffer} using the deflate algorithm.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class ZlibEncoder extends OneToOneEncoder {

    private final ZStream z = new ZStream();
    private final AtomicBoolean finished = new AtomicBoolean();

    // TODO 'do not compress' once option
    // TODO support three wrappers - zlib (default), gzip (unsupported by jzlib, but easy to implement), nowrap
    // TODO Disallow preset dictionary for gzip
    // TODO add close() method
    // FIXME thread safety


    /**
     * Creates a new zlib encoder with the default compression level
     * ({@link JZlib#Z_DEFAULT_COMPRESSION}).
     *
     * @throws ZStreamException if failed to initialize zlib
     */
    public ZlibEncoder() throws ZStreamException {
        this(JZlib.Z_DEFAULT_COMPRESSION);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}.
     *
     * @param compressionLevel
     *        the compression level, as specified in {@link JZlib}.
     *        The common values are
     *        {@link JZlib#Z_BEST_COMPRESSION},
     *        {@link JZlib#Z_BEST_SPEED},
     *        {@link JZlib#Z_DEFAULT_COMPRESSION}, and
     *        {@link JZlib#Z_NO_COMPRESSION}.
     *
     * @throws ZStreamException if failed to initialize zlib
     */
    public ZlibEncoder(int compressionLevel) throws ZStreamException {
        int resultCode = z.deflateInit(compressionLevel, false); // Default: ZLIB format
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        }
    }

    /**
     * Creates a new zlib encoder with the default compression level
     * ({@link JZlib#Z_DEFAULT_COMPRESSION}) and the specified preset
     * dictionary.
     *
     * @param dictionary  the preset dictionary
     *
     * @throws ZStreamException if failed to initialize zlib
     */
    public ZlibEncoder(byte[] dictionary) throws ZStreamException {
        this(JZlib.Z_DEFAULT_COMPRESSION, dictionary);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}
     * and the specified preset dictionary.
     *
     * @param compressionLevel
     *        the compression level, as specified in {@link JZlib}.
     *        The common values are
     *        {@link JZlib#Z_BEST_COMPRESSION},
     *        {@link JZlib#Z_BEST_SPEED},
     *        {@link JZlib#Z_DEFAULT_COMPRESSION}, and
     *        {@link JZlib#Z_NO_COMPRESSION}.
     * @param dictionary  the preset dictionary
     *
     * @throws ZStreamException if failed to initialize zlib
     */
    public ZlibEncoder(int compressionLevel, byte[] dictionary) throws ZStreamException {
        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }

        int resultCode;
        resultCode = z.deflateInit(compressionLevel, false); // Default: ZLIB format
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        } else {
            resultCode = z.deflateSetDictionary(dictionary, dictionary.length);
            if (resultCode != JZlib.Z_OK){
                ZlibUtil.fail(z, "failed to set the dictionary", resultCode);
            }
        }
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer)) {
            return msg;
        }

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
                return ctx.getChannel().getConfig().getBufferFactory().getBuffer(
                        uncompressed.order(), out, 0, z.next_out_index);
            } else {
                return ChannelBuffers.EMPTY_BUFFER;
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
            return Channels.failedFuture(
                    ctx.getChannel(),
                    new ZStreamException("zlib stream closed already"));
        }

        try {
            // Configure input.
            z.next_in = new byte[0];
            z.next_in_index = 0;
            z.avail_in = 0;

            // Configure output.
            byte[] out = new byte[8]; // Minimum room for ADLER32 + ZLIB header
            z.next_out = out;
            z.next_out_index = 0;
            z.avail_out = out.length;

            ChannelFuture future;

            // Write the ADLER32 checksum.
            int resultCode = z.deflate(JZlib.Z_FINISH);
            if (resultCode != JZlib.Z_OK && resultCode != JZlib.Z_STREAM_END) {
                future = Channels.failedFuture(
                        ctx.getChannel(),
                        ZlibUtil.exception(z, "compression failure", resultCode));
            } else if (z.next_out_index != 0) {
                future = Channels.future(ctx.getChannel());
                Channels.write(
                        ctx, future,
                        ctx.getChannel().getConfig().getBufferFactory().getBuffer(
                                out, 0, z.next_out_index));
            } else {
                // Note that we don't return a SucceededChannelFuture
                // just in case any downstream handler or a sink wants to
                // notify a write error.
                future = Channels.future(ctx.getChannel());
                Channels.write(ctx, future, ChannelBuffers.EMPTY_BUFFER);
            }

            if (evt != null) {
                future.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future)
                            throws Exception {
                        ctx.sendDownstream(evt);
                    }
                });
            }

            return future;
        } finally {
            // Deference the external references explicitly to tell the VM that
            // the allocated byte arrays are temporary so that the call stack
            // can be utilized.
            // I'm not sure if the modern VMs do this optimization though.
            z.next_in = null;
            z.next_out = null;
        }
    }
}
