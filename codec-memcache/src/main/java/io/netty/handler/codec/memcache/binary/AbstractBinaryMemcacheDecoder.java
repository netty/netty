/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache.binary;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.memcache.AbstractMemcacheObjectDecoder;
import io.netty.handler.codec.memcache.DefaultLastMemcacheContent;
import io.netty.handler.codec.memcache.DefaultMemcacheContent;
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.handler.codec.memcache.MemcacheContent;
import io.netty.handler.codec.memcache.MemcacheMessage;
import io.netty.util.CharsetUtil;

import java.util.List;

import static io.netty.buffer.ByteBufUtil.*;

/**
 * Decoder for both {@link BinaryMemcacheRequest} and {@link BinaryMemcacheResponse}.
 * <p/>
 * The difference in the protocols (header) is implemented by the subclasses.
 */
public abstract class AbstractBinaryMemcacheDecoder
        <M extends BinaryMemcacheMessage<H>, H extends BinaryMemcacheMessageHeader>
    extends AbstractMemcacheObjectDecoder {

    public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;

    private final int chunkSize;

    private H currentHeader;
    private ByteBuf currentExtras;
    private String currentKey;
    private int alreadyReadChunkSize;

    private State state = State.READ_HEADER;

    /**
     * Create a new {@link AbstractBinaryMemcacheDecoder} with default settings.
     */
    protected AbstractBinaryMemcacheDecoder() {
        this(DEFAULT_MAX_CHUNK_SIZE);
    }

    /**
     * Create a new {@link AbstractBinaryMemcacheDecoder} with custom settings.
     *
     * @param chunkSize the maximum chunk size of the payload.
     */
    protected AbstractBinaryMemcacheDecoder(int chunkSize) {
        if (chunkSize < 0) {
            throw new IllegalArgumentException("chunkSize must be a positive integer: " + chunkSize);
        }

        this.chunkSize = chunkSize;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state) {
            case READ_HEADER: try {
                if (in.readableBytes() < 24) {
                    return;
                }
                resetDecoder();

                currentHeader = decodeHeader(in);
                state = State.READ_EXTRAS;
            } catch (Exception e) {
                out.add(invalidMessage(e));
                return;
            }
            case READ_EXTRAS: try {
                byte extrasLength = currentHeader.getExtrasLength();
                if (extrasLength > 0) {
                    if (in.readableBytes() < extrasLength) {
                        return;
                    }

                    currentExtras = readBytes(ctx.alloc(), in, extrasLength);
                }

                state = State.READ_KEY;
            } catch (Exception e) {
                out.add(invalidMessage(e));
                return;
            }
            case READ_KEY: try {
                short keyLength = currentHeader.getKeyLength();
                if (keyLength > 0) {
                    if (in.readableBytes() < keyLength) {
                        return;
                    }

                    currentKey = in.toString(in.readerIndex(), keyLength, CharsetUtil.UTF_8);
                    in.skipBytes(keyLength);
                }

                out.add(buildMessage(currentHeader, currentExtras, currentKey));
                currentExtras = null;
                state = State.READ_CONTENT;
            } catch (Exception e) {
                out.add(invalidMessage(e));
                return;
            }
            case READ_CONTENT: try {
                int valueLength = currentHeader.getTotalBodyLength()
                    - currentHeader.getKeyLength()
                    - currentHeader.getExtrasLength();
                int toRead = in.readableBytes();
                if (valueLength > 0) {
                    if (toRead == 0) {
                        return;
                    }

                    if (toRead > chunkSize) {
                        toRead = chunkSize;
                    }

                    int remainingLength = valueLength - alreadyReadChunkSize;
                    if (toRead > remainingLength) {
                        toRead = remainingLength;
                    }

                    ByteBuf chunkBuffer = readBytes(ctx.alloc(), in, toRead);

                    MemcacheContent chunk;
                    if ((alreadyReadChunkSize += toRead) >= valueLength) {
                        chunk = new DefaultLastMemcacheContent(chunkBuffer);
                    } else {
                        chunk = new DefaultMemcacheContent(chunkBuffer);
                    }

                    out.add(chunk);
                    if (alreadyReadChunkSize < valueLength) {
                        return;
                    }
                } else {
                    out.add(LastMemcacheContent.EMPTY_LAST_CONTENT);
                }

                state = State.READ_HEADER;
                return;
            } catch (Exception e) {
                out.add(invalidChunk(e));
                return;
            }
            case BAD_MESSAGE:
                in.skipBytes(actualReadableBytes());
                return;
            default:
                throw new Error("Unknown state reached: " + state);
        }
    }

    /**
     * Helper method to create a message indicating a invalid decoding result.
     *
     * @param cause the cause of the decoding failure.
     * @return a valid message indicating failure.
     */
    private M invalidMessage(Exception cause) {
        state = State.BAD_MESSAGE;
        M message = buildInvalidMessage();
        message.setDecoderResult(DecoderResult.failure(cause));
        return message;
    }

    /**
     * Helper method to create a content chunk indicating a invalid decoding result.
     *
     * @param cause the cause of the decoding failure.
     * @return a valid content chunk indicating failure.
     */
    private MemcacheContent invalidChunk(Exception cause) {
        state = State.BAD_MESSAGE;
        MemcacheContent chunk = new DefaultLastMemcacheContent(Unpooled.EMPTY_BUFFER);
        chunk.setDecoderResult(DecoderResult.failure(cause));
        return chunk;
    }

    /**
     * When the channel goes inactive, release all frames to prevent data leaks.
     *
     * @param ctx handler context
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        if (currentExtras != null) {
            currentExtras.release();
        }

        resetDecoder();
    }

    /**
     * Prepare for next decoding iteration.
     */
    protected void resetDecoder() {
        currentHeader = null;
        currentExtras = null;
        currentKey = null;
        alreadyReadChunkSize = 0;
    }

    /**
     * Decode and return the parsed {@link BinaryMemcacheMessageHeader}.
     *
     * @param in the incoming buffer.
     * @return the decoded header.
     */
    protected abstract H decodeHeader(ByteBuf in);

    /**
     * Build the complete message, based on the information decoded.
     *
     * @param header the header of the message.
     * @param extras possible extras.
     * @param key    possible key.
     * @return the decoded message.
     */
    protected abstract M buildMessage(H header, ByteBuf extras, String key);

    /**
     * Helper method to create a upstream message when the incoming parsing did fail.
     *
     * @return a message indicating a decoding failure.
     */
    protected abstract M buildInvalidMessage();

    /**
     * Contains all states this decoder can possibly be in.
     * <p/>
     * Note that most of the states can be optional, the only one required is reading
     * the header ({@link #READ_HEADER}. All other steps depend on the length fields
     * in the header and will be executed conditionally.
     */
    enum State {
        /**
         * Currently reading the header portion.
         */
        READ_HEADER,

        /**
         * Currently reading the extras portion (optional).
         */
        READ_EXTRAS,

        /**
         * Currently reading the key portion (optional).
         */
        READ_KEY,

        /**
         * Currently reading the value chunks (optional).
         */
        READ_CONTENT,

        /**
         * Something went wrong while decoding the message or chunks.
         */
        BAD_MESSAGE
    }

}
