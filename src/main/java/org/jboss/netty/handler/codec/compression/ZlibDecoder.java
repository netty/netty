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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.util.internal.jzlib.JZlib;
import org.jboss.netty.util.internal.jzlib.ZStream;


/**
 * Decompresses a {@link ChannelBuffer} using the deflate algorithm.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2241 $, $Date: 2010-04-16 13:12:43 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.handler.codec.compression.ZlibWrapper
 */
public class ZlibDecoder extends OneToOneDecoder {

    private final ZStream z = new ZStream();
    private volatile boolean finished;

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder() {
        this(ZlibWrapper.ZLIB);
    }

    /**
     * Creates a new instance with the specified wrapper.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder(ZlibWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }

        synchronized (z) {
            int resultCode = z.inflateInit(ZlibUtil.convertWrapperType(wrapper));
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "initialization failure", resultCode);
            }
        }
    }

    /**
     * Creates a new instance with the specified preset dictionary. The wrapper
     * is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public ZlibDecoder(byte[] dictionary) {
        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }

        synchronized (z) {
            int resultCode;
            resultCode = z.inflateInit(JZlib.W_ZLIB);
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "initialization failure", resultCode);
            } else {
                resultCode = z.inflateSetDictionary(dictionary, dictionary.length);
                if (resultCode != JZlib.Z_OK) {
                    ZlibUtil.fail(z, "failed to set the dictionary", resultCode);
                }
            }
        }
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream
     * has been reached.
     */
    public boolean isClosed() {
        return finished;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer) || finished) {
            return msg;
        }

        synchronized (z) {
            try {
                // Configure input.
                ChannelBuffer compressed = (ChannelBuffer) msg;
                byte[] in = new byte[compressed.readableBytes()];
                compressed.readBytes(in);
                z.next_in = in;
                z.next_in_index = 0;
                z.avail_in = in.length;

                // Configure output.
                byte[] out = new byte[in.length << 1];
                ChannelBuffer decompressed = ChannelBuffers.dynamicBuffer(
                        compressed.order(), out.length,
                        ctx.getChannel().getConfig().getBufferFactory());
                z.next_out = out;
                z.next_out_index = 0;
                z.avail_out = out.length;

                do {
                    // Decompress 'in' into 'out'
                    int resultCode = z.inflate(JZlib.Z_SYNC_FLUSH);
                    switch (resultCode) {
                    case JZlib.Z_STREAM_END:
                    case JZlib.Z_OK:
                    case JZlib.Z_BUF_ERROR:
                        decompressed.writeBytes(out, 0, z.next_out_index);
                        z.next_out_index = 0;
                        z.avail_out = out.length;
                        if (resultCode == JZlib.Z_STREAM_END) {
                            finished = true; // Do not decode anymore.
                            z.inflateEnd();
                        }
                        break;
                    default:
                        ZlibUtil.fail(z, "decompression failure", resultCode);
                    }
                } while (z.avail_in > 0);

                if (decompressed.writerIndex() != 0) { // readerIndex is always 0
                    return decompressed;
                } else {
                    return null;
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
    }
}
