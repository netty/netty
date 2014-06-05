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

package io.netty.handler.codec.json;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.channel.ChannelPipeline;

import java.util.List;

/**
 * Splits a byte stream of JSON objects and arrays into individual objects/arrays and passes them up the
 * {@link ChannelPipeline}.
 *
 * This class does not do any real parsing or validation. A sequence of bytes is considered a JSON object/array
 * if it contains a matching number of opening and closing braces/brackets. It's up to a subsequent
 * {@link ChannelHandler} to parse the JSON text into a more usable form i.e. a POJO.
 */
public class JsonObjectDecoder extends ByteToMessageDecoder {

    private int openBraces;
    private int idx;

    private boolean isDecoding;
    private boolean isArrayStreamDecoding;
    private boolean insideString;

    private final int maxObjectLength;
    private final boolean streamArrayElements;

    public JsonObjectDecoder() {
        // 1 MB
        this(1024 * 1024);
    }

    public JsonObjectDecoder(int maxObjectLength) {
        this(maxObjectLength, false);
    }

    public JsonObjectDecoder(boolean streamArrayElements) {
        this(1024 * 1024, streamArrayElements);
    }

    /**
     * @param maxObjectLength   maximum number of bytes a JSON object/array may use (including braces and all).
     *                             Objects exceeding this length are dropped and an {@link TooLongFrameException}
     *                             is thrown.
     * @param streamArrayElements   if set to true and the "top level" JSON object is an array, each of its entries
     *                                  is passed through the pipeline individually and immediately after it was fully
     *                                  received, allowing for arrays with "infinitely" many elements.
     *
     */
    public JsonObjectDecoder(int maxObjectLength, boolean streamArrayElements) {
        if (maxObjectLength < 1) {
            throw new IllegalArgumentException("maxObjectLength must be a positive int");
        }
        this.maxObjectLength = maxObjectLength;
        this.streamArrayElements = streamArrayElements;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // index of next byte to process.
        int idx = this.idx;
        int wrtIdx = in.writerIndex();

        if (wrtIdx > maxObjectLength) {
            // buffer size exceeded maxObjectLength; discarding the complete buffer.
            ctx.fireExceptionCaught(
                    new TooLongFrameException(
                            "object length exceeds " + maxObjectLength + ": " + wrtIdx + " bytes discarded")
            );

            in.skipBytes(in.readableBytes());
            reset();
            return;
        }

        for (/* use current idx */; idx < wrtIdx; idx++) {
            byte c = in.getByte(idx);
            if (isDecoding) {
                decodeByte(c, in, idx);

                // All opening braces/brackets have been closed. That's enough to conclude
                // that the JSON object/array is complete.
                if (openBraces == 0) {
                    ByteBuf json = extractObject(ctx, in, in.readerIndex(), idx + 1 - in.readerIndex());
                    if (json != null) {
                        out.add(json);
                    }

                    // The JSON object/array was extracted => discard the bytes from
                    // the input buffer.
                    in.readerIndex(idx + 1);
                    // Reset the object state to get ready for the next JSON object/text
                    // coming along the byte stream.
                    reset();
                }
            } else if (isArrayStreamDecoding) {
                decodeByte(c, in, idx);

                if (!insideString && (openBraces == 1 && c == ',' || openBraces == 0 && c == ']')) {
                    // skip leading spaces. No range check is needed and the loop will terminate
                    // because the byte at position idx is not a whitespace.
                    for (int i = in.readerIndex(); Character.isWhitespace(in.getByte(i)); i++) {
                        in.skipBytes(1);
                    }

                    // skip trailing spaces.
                    int idxNoSpaces = idx - 1;
                    while (idxNoSpaces >= in.readerIndex() && Character.isWhitespace(in.getByte(idxNoSpaces))) {
                        idxNoSpaces--;
                    }

                    ByteBuf json = extractObject(ctx, in, in.readerIndex(), idxNoSpaces + 1 - in.readerIndex());
                    if (json != null) {
                        out.add(json);
                    }

                    in.readerIndex(idx + 1);

                    if (c == ']') {
                        reset();
                    }
                }
            // JSON object/array detected. Accumulate bytes until all braces/brackets are closed.
            } else if (c == '{' || c == '[') {
                initDecoding(c);

                if (isArrayStreamDecoding) {
                    // Discard the array bracket
                    in.skipBytes(1);
                }
            // Discard leading spaces in front of a JSON object/array.
            } else if (Character.isWhitespace(c)) {
                in.skipBytes(1);
            } else {
                ctx.fireExceptionCaught(
                        new CorruptedFrameException("Invalid JSON received at byte position " + idx + ". " +
                                                     "Hexdump: " + ByteBufUtil.hexDump(in)));
            }
        }

        if (in.readableBytes() == 0) {
            this.idx = 0;
        } else {
            this.idx = idx;
        }
    }

    /**
     * Override this method if you want to filter the json objects/arrays that get passed through the pipeline.
     */
    protected ByteBuf extractObject(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        return buffer.slice(index, length).retain();
    }

    private void decodeByte(byte c, ByteBuf in, int idx) {
        if ((c == '{' || c == '[') && !insideString) {
            openBraces++;
        } else if ((c == '}' || c == ']') && !insideString) {
            openBraces--;
        } else if (c == '"') {
            // start of a new JSON string. It's necessary to detect strings as they may
            // also contain braces/brackets and that could lead to incorrect results.
            if (!insideString) {
                insideString = true;
            // If the double quote wasn't escaped then this is the end of a string.
            } else if (in.getByte(idx - 1) != '\\') {
                insideString = false;
            }
        }
    }

    private void initDecoding(byte openingBrace) {
        openBraces = 1;
        isArrayStreamDecoding = openingBrace == '[' && streamArrayElements;
        isDecoding = !isArrayStreamDecoding;
    }

    private void reset() {
        isDecoding = insideString = isArrayStreamDecoding = false;
        openBraces = 0;
    }
}
