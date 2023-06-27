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
package io.netty.handler.codec.stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.AppendableCharSequence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.handler.codec.stomp.StompConstants.NUL;
import static io.netty.handler.codec.stomp.StompHeaders.ACCEPT_VERSION;
import static io.netty.handler.codec.stomp.StompHeaders.ACK;
import static io.netty.handler.codec.stomp.StompHeaders.CONTENT_LENGTH;
import static io.netty.handler.codec.stomp.StompHeaders.CONTENT_TYPE;
import static io.netty.handler.codec.stomp.StompHeaders.DESTINATION;
import static io.netty.handler.codec.stomp.StompHeaders.HEART_BEAT;
import static io.netty.handler.codec.stomp.StompHeaders.HOST;
import static io.netty.handler.codec.stomp.StompHeaders.ID;
import static io.netty.handler.codec.stomp.StompHeaders.LOGIN;
import static io.netty.handler.codec.stomp.StompHeaders.MESSAGE;
import static io.netty.handler.codec.stomp.StompHeaders.MESSAGE_ID;
import static io.netty.handler.codec.stomp.StompHeaders.PASSCODE;
import static io.netty.handler.codec.stomp.StompHeaders.RECEIPT;
import static io.netty.handler.codec.stomp.StompHeaders.RECEIPT_ID;
import static io.netty.handler.codec.stomp.StompHeaders.SERVER;
import static io.netty.handler.codec.stomp.StompHeaders.SESSION;
import static io.netty.handler.codec.stomp.StompHeaders.SUBSCRIPTION;
import static io.netty.handler.codec.stomp.StompHeaders.TRANSACTION;
import static io.netty.handler.codec.stomp.StompHeaders.VERSION;

/**
 * Encodes a {@link StompFrame} or a {@link StompSubframe} into a {@link ByteBuf}.
 */
public class StompSubframeEncoder extends MessageToMessageEncoder<StompSubframe> {

    private static final int ESCAPE_HEADER_KEY_CACHE_LIMIT = 32;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final FastThreadLocal<LinkedHashMap<CharSequence, CharSequence>> ESCAPE_HEADER_KEY_CACHE =
            new FastThreadLocal<LinkedHashMap<CharSequence, CharSequence>>() {
                @Override
                protected LinkedHashMap<CharSequence, CharSequence> initialValue() throws Exception {
                    LinkedHashMap<CharSequence, CharSequence> cache = new LinkedHashMap<CharSequence, CharSequence>(
                            ESCAPE_HEADER_KEY_CACHE_LIMIT, DEFAULT_LOAD_FACTOR, true) {

                        @Override
                        protected boolean removeEldestEntry(Entry eldest) {
                            return size() > ESCAPE_HEADER_KEY_CACHE_LIMIT;
                        }
                    };

                    cache.put(ACCEPT_VERSION, ACCEPT_VERSION);
                    cache.put(HOST, HOST);
                    cache.put(LOGIN, LOGIN);
                    cache.put(PASSCODE, PASSCODE);
                    cache.put(HEART_BEAT, HEART_BEAT);
                    cache.put(VERSION, VERSION);
                    cache.put(SESSION, SESSION);
                    cache.put(SERVER, SERVER);
                    cache.put(DESTINATION, DESTINATION);
                    cache.put(ID, ID);
                    cache.put(ACK, ACK);
                    cache.put(TRANSACTION, TRANSACTION);
                    cache.put(RECEIPT, RECEIPT);
                    cache.put(MESSAGE_ID, MESSAGE_ID);
                    cache.put(SUBSCRIPTION, SUBSCRIPTION);
                    cache.put(RECEIPT_ID, RECEIPT_ID);
                    cache.put(MESSAGE, MESSAGE);
                    cache.put(CONTENT_LENGTH, CONTENT_LENGTH);
                    cache.put(CONTENT_TYPE, CONTENT_TYPE);

                    return cache;
                }
            };

    @Override
    protected void encode(ChannelHandlerContext ctx, StompSubframe msg, List<Object> out) throws Exception {
        if (msg instanceof StompFrame) {
            StompFrame stompFrame = (StompFrame) msg;
            ByteBuf buf = encodeFullFrame(stompFrame, ctx);

            out.add(convertFullFrame(stompFrame, buf));
        } else if (msg instanceof StompHeadersSubframe) {
            StompHeadersSubframe stompHeadersSubframe = (StompHeadersSubframe) msg;
            ByteBuf buf = ctx.alloc().buffer(headersSubFrameSize(stompHeadersSubframe));
            encodeHeaders(stompHeadersSubframe, buf);

            out.add(convertHeadersSubFrame(stompHeadersSubframe, buf));
        } else if (msg instanceof StompContentSubframe) {
            StompContentSubframe stompContentSubframe = (StompContentSubframe) msg;
            ByteBuf buf = encodeContent(stompContentSubframe, ctx);

            out.add(convertContentSubFrame(stompContentSubframe, buf));
        }
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link StompFrame} full frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertFullFrame(StompFrame original, ByteBuf encoded) {
        return encoded;
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link StompHeadersSubframe} headers sub frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertHeadersSubFrame(StompHeadersSubframe original, ByteBuf encoded) {
        return encoded;
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link StompHeadersSubframe} content sub frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertContentSubFrame(StompContentSubframe original, ByteBuf encoded) {
        return encoded;
    }

    /**
     * Returns a heuristic size for headers (32 bytes per header line) + (2 bytes for colon and eol) + (additional
     * command buffer).
     */
    protected int headersSubFrameSize(StompHeadersSubframe headersSubframe) {
        int estimatedSize = headersSubframe.headers().size() * 34 + 48;
        if (estimatedSize < 128) {
            return 128;
        }

        return Math.max(estimatedSize, 256);
    }

    private ByteBuf encodeFullFrame(StompFrame frame, ChannelHandlerContext ctx) {
        int contentReadableBytes = frame.content().readableBytes();
        ByteBuf buf = ctx.alloc().buffer(headersSubFrameSize(frame) + contentReadableBytes);
        encodeHeaders(frame, buf);

        if (contentReadableBytes > 0) {
            buf.writeBytes(frame.content());
        }

        return buf.writeByte(NUL);
    }

    private static void encodeHeaders(StompHeadersSubframe frame, ByteBuf buf) {
        StompCommand command = frame.command();
        ByteBufUtil.writeUtf8(buf, command.toString());
        buf.writeByte(StompConstants.LF);

        boolean shouldEscape = shouldEscape(command);
        LinkedHashMap<CharSequence, CharSequence> cache = ESCAPE_HEADER_KEY_CACHE.get();
        for (Entry<CharSequence, CharSequence> entry : frame.headers()) {
            CharSequence headerKey = entry.getKey();
            if (shouldEscape) {
                CharSequence cachedHeaderKey = cache.get(headerKey);
                if (cachedHeaderKey == null) {
                    cachedHeaderKey = escape(headerKey);
                    cache.put(headerKey, cachedHeaderKey);
                }
                headerKey = cachedHeaderKey;
            }

            ByteBufUtil.writeUtf8(buf, headerKey);
            buf.writeByte(StompConstants.COLON);

            CharSequence headerValue = shouldEscape? escape(entry.getValue()) : entry.getValue();
            ByteBufUtil.writeUtf8(buf, headerValue);
            buf.writeByte(StompConstants.LF);
        }

        buf.writeByte(StompConstants.LF);
    }

    private static ByteBuf encodeContent(StompContentSubframe content, ChannelHandlerContext ctx) {
        if (content instanceof LastStompContentSubframe) {
            ByteBuf buf = ctx.alloc().buffer(content.content().readableBytes() + 1);
            buf.writeBytes(content.content());
            buf.writeByte(StompConstants.NUL);
            return buf;
        }

        return content.content().retain();
    }

    private static boolean shouldEscape(StompCommand command) {
        return command != StompCommand.CONNECT && command != StompCommand.CONNECTED;
    }

    private static CharSequence escape(CharSequence input) {
        AppendableCharSequence builder = null;
        for (int i = 0; i < input.length(); i++) {
            char chr = input.charAt(i);
            if (chr == '\\') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\\\");
            } else if (chr == ':') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\c");
            } else if (chr == '\n') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\n");
            } else if (chr == '\r') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\r");
            } else if (builder != null) {
                builder.append(chr);
            }
        }

        return builder != null? builder : input;
    }

    private static AppendableCharSequence escapeBuilder(AppendableCharSequence builder, CharSequence input,
                                                        int offset) {
        if (builder != null) {
            return builder;
        }

        // Add extra overhead to the input char sequence to avoid resizing during escaping.
        return new AppendableCharSequence(input.length() + 8).append(input, 0, offset);
    }
}
