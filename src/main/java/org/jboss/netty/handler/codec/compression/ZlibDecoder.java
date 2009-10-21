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
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;
import com.jcraft.jzlib.ZStreamException;

/**
 * Decompresses a {@link ChannelBuffer} using the deflate algorithm.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class ZlibDecoder extends OneToOneDecoder {

    private final ZStream z = new ZStream();

    // TODO Auto-detect wrappers (zlib, gzip, nowrapper as a fallback)

    /**
     * Creates a new instance.
     */
    public ZlibDecoder() {
        z.inflateInit();
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer)) {
            return msg;
        }

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
                    // TODO: Remove myself from the pipeline
                case JZlib.Z_OK:
                case JZlib.Z_BUF_ERROR:
                    decompressed.writeBytes(out, 0, z.next_out_index);
                    z.next_out_index = 0;
                    z.avail_out = out.length;
                    break;
                default:
                    throw new ZStreamException(
                            "decompression failure (" + resultCode + ")" +
                            (z.msg != null? ": " + z.msg : ""));
                }
            } while (z.avail_in > 0);

            if (decompressed.writerIndex() != 0) { // readerIndex is always 0
                return decompressed;
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
}
