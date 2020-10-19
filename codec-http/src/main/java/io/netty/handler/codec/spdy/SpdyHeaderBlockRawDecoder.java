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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.ObjectUtil;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.getSignedInt;

public class SpdyHeaderBlockRawDecoder extends SpdyHeaderBlockDecoder {

    private static final int LENGTH_FIELD_SIZE = 4;

    private final int maxHeaderSize;

    private State state;

    private ByteBuf cumulation;

    private int headerSize;
    private int numHeaders;
    private int length;
    private String name;

    private enum State {
        READ_NUM_HEADERS,
        READ_NAME_LENGTH,
        READ_NAME,
        SKIP_NAME,
        READ_VALUE_LENGTH,
        READ_VALUE,
        SKIP_VALUE,
        END_HEADER_BLOCK,
        ERROR
    }

    public SpdyHeaderBlockRawDecoder(SpdyVersion spdyVersion, int maxHeaderSize) {
        ObjectUtil.checkNotNull(spdyVersion, "spdyVersion");
        this.maxHeaderSize = maxHeaderSize;
        state = State.READ_NUM_HEADERS;
    }

    private static int readLengthField(ByteBuf buffer) {
        int length = getSignedInt(buffer, buffer.readerIndex());
        buffer.skipBytes(LENGTH_FIELD_SIZE);
        return length;
    }

    @Override
    void decode(ByteBufAllocator alloc, ByteBuf headerBlock, SpdyHeadersFrame frame) throws Exception {
        ObjectUtil.checkNotNull(headerBlock, "headerBlock");
        ObjectUtil.checkNotNull(frame, "frame");

        if (cumulation == null) {
            decodeHeaderBlock(headerBlock, frame);
            if (headerBlock.isReadable()) {
                cumulation = alloc.buffer(headerBlock.readableBytes());
                cumulation.writeBytes(headerBlock);
            }
        } else {
            cumulation.writeBytes(headerBlock);
            decodeHeaderBlock(cumulation, frame);
            if (cumulation.isReadable()) {
                cumulation.discardReadBytes();
            } else {
                releaseBuffer();
            }
        }
    }

    protected void decodeHeaderBlock(ByteBuf headerBlock, SpdyHeadersFrame frame) throws Exception {
        int skipLength;
        while (headerBlock.isReadable()) {
            switch(state) {
                case READ_NUM_HEADERS:
                    if (headerBlock.readableBytes() < LENGTH_FIELD_SIZE) {
                        return;
                    }

                    numHeaders = readLengthField(headerBlock);

                    if (numHeaders < 0) {
                        state = State.ERROR;
                        frame.setInvalid();
                    } else if (numHeaders == 0) {
                        state = State.END_HEADER_BLOCK;
                    } else {
                        state = State.READ_NAME_LENGTH;
                    }
                    break;

                case READ_NAME_LENGTH:
                    if (headerBlock.readableBytes() < LENGTH_FIELD_SIZE) {
                        return;
                    }

                    length = readLengthField(headerBlock);

                    // Recipients of a zero-length name must issue a stream error
                    if (length <= 0) {
                        state = State.ERROR;
                        frame.setInvalid();
                    } else if (length > maxHeaderSize || headerSize > maxHeaderSize - length) {
                        headerSize = maxHeaderSize + 1;
                        state = State.SKIP_NAME;
                        frame.setTruncated();
                    } else {
                        headerSize += length;
                        state = State.READ_NAME;
                    }
                    break;

                case READ_NAME:
                    if (headerBlock.readableBytes() < length) {
                        return;
                    }

                    byte[] nameBytes = new byte[length];
                    headerBlock.readBytes(nameBytes);
                    name = new String(nameBytes, "UTF-8");

                    // Check for identically named headers
                    if (frame.headers().contains(name)) {
                        state = State.ERROR;
                        frame.setInvalid();
                    } else {
                        state = State.READ_VALUE_LENGTH;
                    }
                    break;

                case SKIP_NAME:
                    skipLength = Math.min(headerBlock.readableBytes(), length);
                    headerBlock.skipBytes(skipLength);
                    length -= skipLength;

                    if (length == 0) {
                        state = State.READ_VALUE_LENGTH;
                    }
                    break;

                case READ_VALUE_LENGTH:
                    if (headerBlock.readableBytes() < LENGTH_FIELD_SIZE) {
                        return;
                    }

                    length = readLengthField(headerBlock);

                    // Recipients of illegal value fields must issue a stream error
                    if (length < 0) {
                        state = State.ERROR;
                        frame.setInvalid();
                    } else if (length == 0) {
                        if (!frame.isTruncated()) {
                            // SPDY/3 allows zero-length (empty) header values
                            frame.headers().add(name, "");
                        }

                        name = null;
                        if (--numHeaders == 0) {
                            state = State.END_HEADER_BLOCK;
                        } else {
                            state = State.READ_NAME_LENGTH;
                        }

                    } else if (length > maxHeaderSize || headerSize > maxHeaderSize - length) {
                        headerSize = maxHeaderSize + 1;
                        name = null;
                        state = State.SKIP_VALUE;
                        frame.setTruncated();
                    } else {
                        headerSize += length;
                        state = State.READ_VALUE;
                    }
                    break;

                case READ_VALUE:
                    if (headerBlock.readableBytes() < length) {
                        return;
                    }

                    byte[] valueBytes = new byte[length];
                    headerBlock.readBytes(valueBytes);

                    // Add Name/Value pair to headers
                    int index = 0;
                    int offset = 0;

                    // Value must not start with a NULL character
                    if (valueBytes[0] == (byte) 0) {
                        state = State.ERROR;
                        frame.setInvalid();
                        break;
                    }

                    while (index < length) {
                        while (index < valueBytes.length && valueBytes[index] != (byte) 0) {
                            index ++;
                        }
                        if (index < valueBytes.length) {
                            // Received NULL character
                            if (index + 1 == valueBytes.length || valueBytes[index + 1] == (byte) 0) {
                                // Value field ended with a NULL character or
                                // received multiple, in-sequence NULL characters.
                                // Recipients of illegal value fields must issue a stream error
                                state = State.ERROR;
                                frame.setInvalid();
                                break;
                            }
                        }
                        String value = new String(valueBytes, offset, index - offset, "UTF-8");

                        try {
                            frame.headers().add(name, value);
                        } catch (IllegalArgumentException e) {
                            // Name contains NULL or non-ascii characters
                            state = State.ERROR;
                            frame.setInvalid();
                            break;
                        }
                        index ++;
                        offset = index;
                    }

                    name = null;

                    // If we broke out of the add header loop, break here
                    if (state == State.ERROR) {
                        break;
                    }

                    if (--numHeaders == 0) {
                        state = State.END_HEADER_BLOCK;
                    } else {
                        state = State.READ_NAME_LENGTH;
                    }
                    break;

                case SKIP_VALUE:
                    skipLength = Math.min(headerBlock.readableBytes(), length);
                    headerBlock.skipBytes(skipLength);
                    length -= skipLength;

                    if (length == 0) {
                        if (--numHeaders == 0) {
                            state = State.END_HEADER_BLOCK;
                        } else {
                            state = State.READ_NAME_LENGTH;
                        }
                    }
                    break;

                case END_HEADER_BLOCK:
                    state = State.ERROR;
                    frame.setInvalid();
                    break;

                case ERROR:
                    headerBlock.skipBytes(headerBlock.readableBytes());
                    return;

                default:
                    throw new Error("Shouldn't reach here.");
            }
        }
    }

    @Override
    void endHeaderBlock(SpdyHeadersFrame frame) throws Exception {
        if (state != State.END_HEADER_BLOCK) {
            frame.setInvalid();
        }

        releaseBuffer();

        // Initialize header block decoding fields
        headerSize = 0;
        name = null;
        state = State.READ_NUM_HEADERS;
    }

    @Override
    void end() {
        releaseBuffer();
    }

    private void releaseBuffer() {
        if (cumulation != null) {
            cumulation.release();
            cumulation = null;
        }
    }
}
