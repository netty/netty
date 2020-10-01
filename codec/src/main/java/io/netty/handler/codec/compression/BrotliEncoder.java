/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.compression;

import com.nixxcode.jvmbrotli.common.BrotliLoader;
import com.nixxcode.jvmbrotli.enc.BrotliOutputStream;
import com.nixxcode.jvmbrotli.enc.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Brotli Encoder (Compressor)
 */
public class BrotliEncoder extends MessageToByteEncoder<ByteBuf> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BrotliEncoder.class);

    static {
        logger.info("Brotli Loader Status: {}", BrotliLoader.isBrotliAvailable());
    }

    private final Encoder.Parameters parameters;
    private BrotliOutputStream brotliOutputStream;
    private ByteBuf byteBuf;

    /**
     * Create new {@link BrotliEncoder} Instance
     *
     * @param quality Quality of Encoding (Compression)
     */
    public BrotliEncoder(int quality) {
        this.parameters = new Encoder.Parameters().setQuality(quality);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        if (msg.readableBytes() == 0) {
            out.writeBytes(msg);
            return;
        }

        if (brotliOutputStream == null) {
            byteBuf = ctx.alloc().buffer();
            brotliOutputStream = new BrotliOutputStream(new ByteBufOutputStream(byteBuf), parameters);
        }

        if (msg.hasArray()) {
            byte[] inAry = msg.array();
            int offset = msg.arrayOffset() + msg.readerIndex();
            int len = msg.readableBytes();
            brotliOutputStream.write(inAry, offset, len);
        } else {
            brotliOutputStream.write(ByteBufUtil.getBytes(msg));
        }

        brotliOutputStream.flush();
        out.writeBytes(byteBuf);
        byteBuf.clear();

        if (!out.isWritable()) {
            out.ensureWritable(out.writerIndex());
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (brotliOutputStream != null) {
            brotliOutputStream.close();
            byteBuf.release();
            promise.setSuccess();
            brotliOutputStream = null;
        }
    }
}
