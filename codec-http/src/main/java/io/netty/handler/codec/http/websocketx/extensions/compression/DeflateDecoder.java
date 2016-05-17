/*
 * Copyright 2014 The Netty Project
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

import java.util.List;

/**
 * Deflate implementation of a payload decompressor for
 * <tt>io.netty.handler.codec.http.websocketx.WebSocketFrame</tt>.
 */
abstract class DeflateDecoder extends WebSocketExtensionDecoder {

    static final byte[] FRAME_TAIL = new byte[] {0x00, 0x00, (byte) 0xff, (byte) 0xff};

    private final boolean noContext;

    private EmbeddedChannel decoder;

    /**
     * Constructor
     * @param noContext true to disable context takeover.
     */
    public DeflateDecoder(boolean noContext) {
        this.noContext = noContext;
    }

    protected abstract boolean appendFrameTail(WebSocketFrame msg);

    protected abstract int newRsv(WebSocketFrame msg);

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
        if (decoder == null) {
            if (!(msg instanceof TextWebSocketFrame) && !(msg instanceof BinaryWebSocketFrame)) {
                throw new CodecException("unexpected initial frame type: " + msg.getClass().getName());
            }
            decoder = new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE));
        }

        boolean readable = msg.content().isReadable();
        decoder.writeInbound(msg.content().retain());
        if (appendFrameTail(msg)) {
            decoder.writeInbound(Unpooled.wrappedBuffer(FRAME_TAIL));
        }

        CompositeByteBuf compositeUncompressedContent = ctx.alloc().compositeBuffer();
        for (;;) {
            ByteBuf partUncompressedContent = decoder.readInbound();
            if (partUncompressedContent == null) {
                break;
            }
            if (!partUncompressedContent.isReadable()) {
                partUncompressedContent.release();
                continue;
            }
            compositeUncompressedContent.addComponent(true, partUncompressedContent);
        }
        // Correctly handle empty frames
        // See https://github.com/netty/netty/issues/4348
        if (readable && compositeUncompressedContent.numComponents() <= 0) {
            compositeUncompressedContent.release();
            throw new CodecException("cannot read uncompressed buffer");
        }

        if (msg.isFinalFragment() && noContext) {
            cleanup();
        }

        WebSocketFrame outMsg;
        if (msg instanceof TextWebSocketFrame) {
            outMsg = new TextWebSocketFrame(msg.isFinalFragment(), newRsv(msg), compositeUncompressedContent);
        } else if (msg instanceof BinaryWebSocketFrame) {
            outMsg = new BinaryWebSocketFrame(msg.isFinalFragment(), newRsv(msg), compositeUncompressedContent);
        } else if (msg instanceof ContinuationWebSocketFrame) {
            outMsg = new ContinuationWebSocketFrame(msg.isFinalFragment(), newRsv(msg),
                    compositeUncompressedContent);
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

    private void cleanup() {
        if (decoder != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            if (decoder.finish()) {
                for (;;) {
                    ByteBuf buf = decoder.readOutbound();
                    if (buf == null) {
                        break;
                    }
                    // Release the buffer
                    buf.release();
                }
            }
            decoder = null;
        }
    }
}
