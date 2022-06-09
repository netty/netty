/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http.websocketx.extensions.compression;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.util.Send;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.CodecException;
import io.netty5.handler.codec.compression.ZlibCodecFactory;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocketFrame;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.DeflateDecoder.FRAME_TAIL_LENGTH;

/**
 * Deflate implementation of a payload compressor for
 * <tt>io.netty5.handler.codec.http.websocketx.WebSocketFrame</tt>.
 */
abstract class DeflateEncoder extends WebSocketExtensionEncoder {
    static final Supplier<Buffer> EMPTY_DEFLATE_BLOCK;
    static final int EMPTY_DEFLATE_BLOCK_LENGTH;
    static {
        byte[] emptyDeflate = { 0x00 };
        EMPTY_DEFLATE_BLOCK = preferredAllocator().constBufferSupplier(emptyDeflate);
        EMPTY_DEFLATE_BLOCK_LENGTH = emptyDeflate.length;
    }

    private final int compressionLevel;
    private final int windowSize;
    private final boolean noContext;
    private final WebSocketExtensionFilter extensionEncoderFilter;

    private EmbeddedChannel encoder;

    /**
     * Constructor
     * @param compressionLevel compression level of the compressor.
     * @param windowSize maximum size of the window compressor buffer.
     * @param noContext true to disable context takeover.
     * @param extensionEncoderFilter extension encoder filter.
     */
    DeflateEncoder(int compressionLevel, int windowSize, boolean noContext,
                   WebSocketExtensionFilter extensionEncoderFilter) {
        this.compressionLevel = compressionLevel;
        this.windowSize = windowSize;
        this.noContext = noContext;
        this.extensionEncoderFilter = Objects.requireNonNull(extensionEncoderFilter, "extensionEncoderFilter");
    }

    /**
     * Returns the extension encoder filter.
     */
    protected WebSocketExtensionFilter extensionEncoderFilter() {
        return extensionEncoderFilter;
    }

    /**
     * @param msg the current frame.
     * @return the rsv bits to set in the compressed frame.
     */
    protected abstract int rsv(WebSocketFrame msg);

    /**
     * @param msg the current frame.
     * @return true if compressed payload tail needs to be removed.
     */
    protected abstract boolean removeFrameTail(WebSocketFrame msg);

    @Override
    protected void encodeAndClose(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
        final Buffer compressedContent;
        if (msg.binaryData().readableBytes() > 0) {
            compressedContent = compressContent(ctx, msg);
        } else if (msg.isFinalFragment()) {
            // Set empty DEFLATE block manually for unknown buffer size
            // https://tools.ietf.org/html/rfc7692#section-7.2.3.6
            compressedContent = EMPTY_DEFLATE_BLOCK.get();
        } else {
            msg.close();
            throw new CodecException("cannot compress content buffer");
        }

        final WebSocketFrame outMsg;
        if (msg instanceof TextWebSocketFrame) {
            outMsg = new TextWebSocketFrame(msg.isFinalFragment(), rsv(msg), compressedContent);
        } else if (msg instanceof BinaryWebSocketFrame) {
            outMsg = new BinaryWebSocketFrame(msg.isFinalFragment(), rsv(msg), compressedContent);
        } else if (msg instanceof ContinuationWebSocketFrame) {
            outMsg = new ContinuationWebSocketFrame(msg.isFinalFragment(), rsv(msg), compressedContent);
        } else {
            compressedContent.close();
            throw new CodecException("unexpected frame type: " + msg.getClass().getName());
        }

        out.add(outMsg);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.handlerRemoved(ctx);
    }

    private Buffer compressContent(ChannelHandlerContext ctx, WebSocketFrame msg) {
        if (encoder == null) {
            encoder = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.NONE, compressionLevel, windowSize, 8));
        }

        encoder.writeOutbound(msg.binaryData());

        List<Send<Buffer>> bufferList = new ArrayList<>();
        for (;;) {
            Buffer partCompressedContent = encoder.readOutbound();
            if (partCompressedContent == null) {
                break;
            }
            if (partCompressedContent.readableBytes() == 0) {
                partCompressedContent.close();
                continue;
            }
            if (partCompressedContent.readerOffset() != 0) {
                // We can't compose buffers that will have reader-offset gaps.
                partCompressedContent.readSplit(0).close(); // Trim off already-read bytes at the beginning.
            }
            if (partCompressedContent.writableBytes() > 0) {
                // We also can't compose buffers that will have writer-offset gaps.
                // Trim off the excess with split.
                bufferList.add(partCompressedContent.split().send());
                partCompressedContent.close();
            } else {
                bufferList.add(partCompressedContent.send());
            }
        }

        if (bufferList.isEmpty()) {
            throw new CodecException("cannot read compressed buffer");
        }

        if (msg.isFinalFragment() && noContext) {
            cleanup();
        }

        Buffer compressedContent;
        CompositeBuffer fullCompressedContent = ctx.bufferAllocator().compose(bufferList);
        if (removeFrameTail(msg)) {
            int realLength = fullCompressedContent.readableBytes() - FRAME_TAIL_LENGTH;
            compressedContent = fullCompressedContent.readerOffset(0).writerOffset(realLength);
        } else {
            compressedContent = fullCompressedContent;
        }

        return compressedContent;
    }

    private void cleanup() {
        if (encoder != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            encoder.finishAndReleaseAll();
            encoder = null;
        }
    }
}
