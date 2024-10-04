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

package io.netty.handler.codec.json;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.channel.ChannelPipeline;

import java.util.List;

/**
 * Splits a byte stream of JSON objects and arrays into individual objects/arrays and passes them up the
 * {@link ChannelPipeline}.
 * <p>
 * The byte stream is expected to be in UTF-8 character encoding or ASCII. The current implementation
 * uses direct {@code byte} to {@code char} cast and then compares that {@code char} to a few low range
 * ASCII characters like {@code '{'}, {@code '['} or {@code '"'}. UTF-8 is not using low range [0..0x7F]
 * byte values for multibyte codepoint representations therefore fully supported by this implementation.
 * <p>
 * This class does not do any real parsing or validation. A sequence of bytes is considered a JSON object/array
 * if it contains a matching number of opening and closing braces/brackets. It's up to a subsequent
 * {@link ChannelHandler} to parse the JSON text into a more usable form i.e. a POJO.
 */
public class JsonObjectDecoder extends ByteToMessageDecoder {

    private static final int ST_CORRUPTED = -1;
    private static final int ST_INIT = 0;
    private static final int ST_DECODING_NORMAL = 1;
    private static final int ST_DECODING_ARRAY_STREAM = 2;

    private int openBraces;
    private int idx;

    private int lastReaderIndex;

    private int state;
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
        this.maxObjectLength = checkPositive(maxObjectLength, "maxObjectLength");
        this.streamArrayElements = streamArrayElements;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (state == ST_CORRUPTED) {
            in.skipBytes(in.readableBytes());
            return;
        }

        if (this.idx > in.readerIndex() && lastReaderIndex != in.readerIndex()) {
            this.idx = in.readerIndex() + (idx - lastReaderIndex);
        }

        // index of next byte to process.
        int idx = this.idx;
        int wrtIdx = in.writerIndex();

        if (wrtIdx > maxObjectLength) {
            // buffer size exceeded maxObjectLength; discarding the complete buffer.
            in.skipBytes(in.readableBytes());
            reset();
            throw new TooLongFrameException(
                            "object length exceeds " + maxObjectLength + ": " + wrtIdx + " bytes discarded");
        }

        for (/* use current idx */; idx < wrtIdx; idx++) {
            byte c = in.getByte(idx);
            if (state == ST_DECODING_NORMAL) {
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
            } else if (state == ST_DECODING_ARRAY_STREAM) {
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

                if (state == ST_DECODING_ARRAY_STREAM) {
                    // Discard the array bracket
                    in.skipBytes(1);
                }
            // Discard leading spaces in front of a JSON object/array.
            } else if (Character.isWhitespace(c)) {
                in.skipBytes(1);
            } else {
                state = ST_CORRUPTED;
                throw new CorruptedFrameException(
                        "invalid JSON received at byte position " + idx + ": " + ByteBufUtil.hexDump(in));
            }
        }

        if (in.readableBytes() == 0) {
            this.idx = 0;
        } else {
            this.idx = idx;
        }
        this.lastReaderIndex = in.readerIndex();
    }

    /**
     * Override this method if you want to filter the json objects/arrays that get passed through the pipeline.
     */
    @SuppressWarnings("UnusedParameters")
    protected ByteBuf extractObject(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        return buffer.retainedSlice(index, length);
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
            } else {
                int backslashCount = 0;
                idx--;
                while (idx >= 0) {
                    if (in.getByte(idx) == '\\') {
                        backslashCount++;
                        idx--;
                    } else {
                        break;
                    }
                }
                // The double quote isn't escaped only if there are even "\"s.
                if (backslashCount % 2 == 0) {
                    // Since the double quote isn't escaped then this is the end of a string.
                    insideString = false;
                }
            }
        }
    }

    private void initDecoding(byte openingBrace) {
        openBraces = 1;
        if (openingBrace == '[' && streamArrayElements) {
            state = ST_DECODING_ARRAY_STREAM;
        } else {
            state = ST_DECODING_NORMAL;
        }
    }

    private void reset() {
        insideString = false;
        state = ST_INIT;
        openBraces = 0;
    }
}
