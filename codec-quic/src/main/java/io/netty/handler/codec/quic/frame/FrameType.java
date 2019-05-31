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
        QuicFrame constructFrame(byte firstByte) {
            return new PaddingFrame();
        }
    },
    PING(0x01),
    ACK(0x02, 0x03),//TODO
    RESET_STREAM(0x04) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new StreamResetFrame();
        }
    },
    STOP_SENDING(0x05),//TODO
    CRYPTO(0x06) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new CryptFrame();
        }
    },
    NEW_TOKEN(0x07),//TODO
    /* Stream has identifiers ranging from 8 to 15(0x0f) */
    STREAM() {

        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new StreamFrame(firstByte);
        }

        @Override
        public byte firstIdentifier() {
            return 0x08;
        }

        @Override
        public boolean match(byte id) {
            return id >= firstIdentifier() && id <= 0x0f;
        }

        @Override
        public byte[] getIdentifiers() {
            byte[] id = new byte[0x0f - firstIdentifier()];
            for (byte i = firstIdentifier(); i < 0x0f; i++) {
                id[i - 8] = i;
            }
            return id;
        }
    },
    MAX_DATA(0x10) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new MaxDataFrame();
        }
    },
    MAX_STREAM_DATA(0x11) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new MaxStreamDataFrame();
        }
    },
    MAX_STREAMS(0x12, 0x13) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new MaxStreamsFrame(firstByte);
        }
    },
    DATA_BLOCKED(0x14) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new DataBlockedFrame();
        }
    },
    STREAM_DATA_BLOCKED(0x15) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new StreamDataBlockedFrame();
        }
    },
    STREAMS_BLOCKED(0x16, 0x17),//TODO
    NEW_CONNECTION_ID(0x18),//TODO
    RETIRE_CONNECTION_ID(0x19),//TODO
    PATH_CHALLENGE(0x1a),//TODO
    PATH_RESPONSE(0x1b),//TODO
    //TODO MERGE
    CONNECTION_CLOSE(0x1c) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new CloseFrame(false);
        }
    },
    APPLICATION_CLOSE(0x1d) {
        @Override
        QuicFrame constructFrame(byte firstByte) {
            return new CloseFrame(true);
        }
    };

    private byte[] id;

    FrameType(int... id) {
        this.id = new byte[id.length];
        for (int i = 0; i < id.length; i++) {
            this.id[i] = (byte) id[i];
        }
    }

    public static QuicFrame readFrame(ByteBuf buf) {
        byte b = buf.readByte();
        FrameType type = typeById(b);
        if (type == null) {
            throw new IllegalStateException("Frame Type not supported: " + b);
        }
        QuicFrame frame = type.constructFrame(b);
        frame.read(buf);
        return frame;
    }

    public static FrameType typeById(byte id) {
    //TODO use maps?
        for (FrameType type : values()) {
            if (type.match(id)) {
                return type;
            }
        }
        return null;
    }

    public boolean match(byte id) {
        for (byte b : this.id) {
            if (b == id) {
                return true;
            }
        }
        return false;
    }

    public byte[] getIdentifiers() {
        return id;
    }

    public byte firstIdentifier() {
        return getIdentifiers()[0];
    }

    QuicFrame constructFrame(byte firstByte) {
        return new QuicFrame(this, firstByte);
    }

}
