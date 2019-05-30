/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;

public enum FrameType {

    PADDING(0x00) {
        @Override
        QuicFrame constructFrame() {
            return new PaddingFrame();
        }
    },
    PING(0x01),
    RESET_STREAM(0x04) {
        @Override
        QuicFrame constructFrame() {
            return new StreamResetFrame();
        }
    },
    CRYPT(0x06) {
        @Override
        QuicFrame constructFrame() {
            return new CryptFrame();
        }
    },
    MAX_DATA(0x10) {
        @Override
        QuicFrame constructFrame() {
            return new MaxDataFrame();
        }
    },
    MAX_STREAM_DATA(0x11) {
        @Override
        QuicFrame constructFrame() {
            return new MaxStreamDataFrame();
        }
    },
    MAX_STREAMS(0x12) {
        @Override
        QuicFrame constructFrame() {
            return new MaxStreamsFrame();
        }
    },
    DATA_LIMIT(0x14) {
        @Override
        QuicFrame constructFrame() {
            return new DataLimitFrame();
        }
    },
    CONNECTION_CLOSE(0x1c) {
        @Override
        QuicFrame constructFrame() {
            return new CloseFrame(false);
        }
    },
    APPLICATION_CLOSE(0x1d) {
        @Override
        QuicFrame constructFrame() {
            return new CloseFrame(true);
        }
    };

    private byte id;

    FrameType(int id) {
        this.id = (byte) id;
    }

    public static QuicFrame readFrame(ByteBuf buf) {
        byte b = buf.readByte();
        //TODO use maps?
        for (FrameType type : values()) {
            if (b == type.id) {
                QuicFrame frame = type.constructFrame();
                frame.read(buf);
                return frame;
            }
        }
        throw new IllegalStateException("Frame Type not supported: " + b);
    }

    public byte getByte() {
        return id;
    }

    QuicFrame constructFrame() {
        return new QuicFrame(this);
    }

}
