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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;

import java.util.List;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * Deflate implementation of a payload decompressor for
 * <tt>io.netty.handler.codec.http.websocketx.WebSocketFrame</tt>.
 */
abstract class DeflateDecoder extends WebSocketExtensionDecoder {

    static final ByteBuf FRAME_TAIL = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(new byte[] {0x00, 0x00, (byte) 0xff, (byte) 0xff}))
            .asReadOnly();

    static final ByteBuf EMPTY_DEFLATE_BLOCK = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(new byte[] { 0x00 }))
            .asReadOnly();

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
        this.extensionDecoderFilter = checkNotNull(extensionDecoderFilter, "extensionDecoderFilter");
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
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
        final ByteBuf decompressedContent = decompressContent(ctx, msg);

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

        out.add(outMsg);
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

    private ByteBuf decompressContent(ChannelHandlerContext ctx, WebSocketFrame msg) {
        if (decoder == null) {
            if (!(msg instanceof TextWebSocketFrame) && !(msg instanceof BinaryWebSocketFrame)) {
                throw new CodecException("unexpected initial frame type: " + msg.getClass().getName());
            }
            decoder = new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE));
        }

        boolean readable = msg.content().isReadable();
        boolean emptyDeflateBlock = EMPTY_DEFLATE_BLOCK.equals(msg.content());

        decoder.writeInbound(msg.content().retain());
        if (appendFrameTail(msg)) {
            decoder.writeInbound(FRAME_TAIL.duplicate());
        }

        CompositeByteBuf compositeDecompressedContent = ctx.alloc().compositeBuffer();
        for (;;) {
            ByteBuf partUncompressedContent = decoder.readInbound();
            if (partUncompressedContent == null) {
                break;
            }
            if (!partUncompressedContent.isReadable()) {
                partUncompressedContent.release();
                continue;
            }
            compositeDecompressedContent.addComponent(true, partUncompressedContent);
        }
        // Correctly handle empty frames
        // See https://github.com/netty/netty/issues/4348
        if (!emptyDeflateBlock && readable && compositeDecompressedContent.numComponents() <= 0) {
            // Sometimes after fragmentation the last frame
            // May contain left-over data that doesn't affect decompression
            if (!(msg instanceof ContinuationWebSocketFrame)) {
                compositeDecompressedContent.release();
                throw new CodecException("cannot read uncompressed buffer");
            }
        }

        if (msg.isFinalFragment() && noContext) {
            cleanup();
        }

        return compositeDecompressedContent;
    }

    private void cleanup() {
        if (decoder != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            decoder.finishAndReleaseAll();
            decoder = null;
        }
    }
}
