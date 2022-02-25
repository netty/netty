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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.CodecException;
import io.netty5.handler.codec.compression.ZlibCodecFactory;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocketFrame;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;

import java.util.Objects;
import java.util.function.Supplier;

import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.extractOrCopy;
import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.intoByteBuf;

/**
 * Deflate implementation of a payload decompressor for
 * <tt>io.netty5.handler.codec.http.websocketx.WebSocketFrame</tt>.
 */
abstract class DeflateDecoder extends WebSocketExtensionDecoder {

    static final Supplier<Buffer> FRAME_TAIL;
    static final int FRAME_TAIL_LENGTH;
    static {
        byte[] frameTail = { 0x00, 0x00, (byte) 0xff, (byte) 0xff };
        FRAME_TAIL = DefaultBufferAllocators.preferredAllocator().constBufferSupplier(frameTail);
        FRAME_TAIL_LENGTH = frameTail.length;
    }

    private final boolean noContext;
    private final WebSocketExtensionFilter extensionDecoderFilter;

    private EmbeddedChannel decoder;

    /**
     * Constructor
     *
     * @param noContext true to disable context takeover.
     * @param extensionDecoderFilter extension decoder filter.
     */
    DeflateDecoder(boolean noContext, WebSocketExtensionFilter extensionDecoderFilter) {
        this.noContext = noContext;
        this.extensionDecoderFilter = Objects.requireNonNull(extensionDecoderFilter, "extensionDecoderFilter");
    }

    /**
     * Returns the extension decoder filter.
     */
    protected WebSocketExtensionFilter extensionDecoderFilter() {
        return extensionDecoderFilter;
    }

    protected abstract boolean appendFrameTail(WebSocketFrame msg);

    protected abstract int newRsv(WebSocketFrame msg);

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        final Buffer decompressedContent = decompressContent(ctx, msg);

        final WebSocketFrame outMsg;
        if (msg instanceof TextWebSocketFrame) {
            outMsg = new TextWebSocketFrame(msg.isFinalFragment(), newRsv(msg), decompressedContent);
        } else if (msg instanceof BinaryWebSocketFrame) {
            outMsg = new BinaryWebSocketFrame(msg.isFinalFragment(), newRsv(msg), decompressedContent);
        } else if (msg instanceof ContinuationWebSocketFrame) {
            outMsg = new ContinuationWebSocketFrame(msg.isFinalFragment(), newRsv(msg), decompressedContent);
        } else {
            throw new CodecException("unexpected frame type: " + msg.getClass().getName());
        }

        ctx.fireChannelRead(outMsg);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.channelInactive(ctx);
    }

    private Buffer decompressContent(ChannelHandlerContext ctx, WebSocketFrame msg) {
        if (decoder == null) {
            if (!(msg instanceof TextWebSocketFrame) && !(msg instanceof BinaryWebSocketFrame)) {
                throw new CodecException("unexpected initial frame type: " + msg.getClass().getName());
            }
            decoder = new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE));
        }

        boolean readable = msg.binaryData().readableBytes() > 0;
        boolean emptyDeflateBlock = isEmptyDeflateBlock(msg.binaryData());

        decoder.writeInbound(intoByteBuf(msg.binaryData()));
        if (appendFrameTail(msg)) {
            decoder.writeInbound(intoByteBuf(FRAME_TAIL.get()));
        }

        CompositeBuffer compositeDecompressedContent = CompositeBuffer.compose(ctx.bufferAllocator());
        for (;;) {
            ByteBuf partUncompressedContent = decoder.readInbound();
            if (partUncompressedContent == null) {
                break;
            }
            if (!partUncompressedContent.isReadable()) {
                partUncompressedContent.release();
                continue;
            }
            compositeDecompressedContent.extendWith(extractOrCopy(ctx.bufferAllocator(),
                    partUncompressedContent).send());
        }
        // Correctly handle empty frames
        // See https://github.com/netty/netty/issues/4348
        if (!emptyDeflateBlock && readable && compositeDecompressedContent.countReadableComponents() <= 0) {
            // Sometimes after fragmentation the last frame
            // May contain left-over data that doesn't affect decompression
            if (!(msg instanceof ContinuationWebSocketFrame)) {
                compositeDecompressedContent.close();
                throw new CodecException("cannot read uncompressed buffer");
            }
        }

        if (msg.isFinalFragment() && noContext) {
            cleanup();
        }

        return compositeDecompressedContent;
    }

    private static boolean isEmptyDeflateBlock(Buffer binaryData) {
        return binaryData.readableBytes() == 1 && binaryData.getByte(binaryData.readerOffset()) == 0;
    }

    private void cleanup() {
        if (decoder != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            decoder.finishAndReleaseAll();
            decoder = null;
        }
    }
}
