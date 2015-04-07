/*
 * Copyright 2015 The Netty Project
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
package io.netty.example.udt.file;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.util.List;

/**
 * Decodes the binary representation of a {@link UdtFileMessage}.
 */
public class UdtFileResponseDecoder extends ByteToMessageDecoder {

    public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;
    private State state = State.READ_HEADER;
    private UdtFileMessage header;
    private int alreadyReadBytes;
    private final int chunkSize;
    private boolean isSendFileOperation = false;

    public UdtFileResponseDecoder() {
        this(DEFAULT_MAX_CHUNK_SIZE);
    }

    public UdtFileResponseDecoder(int chunkSize) {
        if (chunkSize > 0) {
            this.chunkSize = chunkSize;
        } else {
            this.chunkSize = DEFAULT_MAX_CHUNK_SIZE;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state) {
            case READ_HEADER:
                if (in.readableBytes() < 10) {
                    return;
                }
                in.markReaderIndex();

                alreadyReadBytes = 0;
                header = decodeHeader(in);
                if (header.magic() != UdtFileMessage.MAGIC_BYTE) {
                    in.resetReaderIndex();
                    throw new CorruptedFrameException("Invalid magic number: " + header.magic());
                }

                if (header.opcode() == UdtFileOpcodes.SND_FILE) {
                    isSendFileOperation = true;
                } else {
                    isSendFileOperation = false;
                }

                state = State.READ_MESSAGE;
                return;
            case READ_MESSAGE:
                int messageLength = header.messageLength();
                if (messageLength > 0) {
                    if (in.readableBytes() < messageLength) {
                        return;
                    }
                    if (isSendFileOperation) {
                        String fileName = in.toString(in.readerIndex(), messageLength, CharsetUtil.UTF_8);
                        in.skipBytes(messageLength);
                        out.add(new File(fileName));
                        state = State.READ_FILE;
                    } else {
                        byte[] message = new byte[messageLength];
                        in.readBytes(message);
                        out.add(new String(message));
                        state = State.READ_HEADER;
                    }
                } else {
                    state = State.READ_HEADER;
                }
                return;
            case READ_FILE:
                int fileSize = header.fileLength();
                if (fileSize > 0) {
                    int toRead = in.readableBytes();
                    if (toRead == 0) {
                        return;
                    }

                    if (toRead > chunkSize) {
                        toRead = chunkSize;
                    }

                    int remainingBytes = fileSize - alreadyReadBytes;
                    if (toRead > remainingBytes) {
                        toRead = remainingBytes;
                    }

                    ByteBuf chunkBuf = ByteBufUtil.readBytes(ctx.alloc(), in, toRead);
                    alreadyReadBytes += toRead;
                    out.add(chunkBuf);

                    if (alreadyReadBytes >= fileSize) {
                        out.add(new LastUdtFileMessage());
                    }

                    if (alreadyReadBytes < fileSize) {
                        return;
                    }
                } else {
                    out.add(new LastUdtFileMessage());
                }
                state = State.READ_HEADER;
                return;
            default:
                break;
        }
    }

    private UdtFileMessage decodeHeader(ByteBuf in) {
        UdtFileMessage header = new UdtFileMessage();
        header.setMagic(in.readByte());
        header.setOpcode(in.readByte());
        header.setMessageLength(in.readInt());
        header.setFileLength(in.readInt());
        return header;
    }

    private enum State {
        READ_HEADER,
        READ_MESSAGE,
        READ_FILE
    }
}
