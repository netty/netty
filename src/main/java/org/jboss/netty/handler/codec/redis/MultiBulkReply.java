/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.frame.CorruptedFrameException;

/**
 * {@link Reply} which contains a bulk of {@link Reply}'s
 */
public class MultiBulkReply extends Reply {
    static final char MARKER = '*';

    // State
    private Object[] values;
    private int size;
    private int num;

    /**
     * Creates a new instance with empty values.
     */
    public MultiBulkReply() {
    }

    /**
     * Creates a new instance with the specified values.
     *
     * @param values an array whose elements are either {@link ChannelBuffer} or {@link Number}.
     */
    public MultiBulkReply(Object... values) {
        if (values != null) {
            for (Object v: values) {
                if (v == null) {
                    continue;
                }
                if (!(v instanceof ChannelBuffer || v instanceof Number)) {
                    throw new IllegalArgumentException(
                            "values contains an element whose type is neither " +
                            ChannelBuffer.class.getSimpleName() + " nor " + Number.class.getSimpleName() + ": " +
                            v.getClass().getName());
                }
            }
            this.values = values;
        }
    }

    /**
     * Returns an array whose elements are either {@link ChannelBuffer} or {@link Number}.
     */
    public Object[] values() {
        return values;
    }
    
    void read(RedisReplyDecoder decoder, ChannelBuffer in) throws Exception {
        // If we attempted to read the size before, skip the '*' and reread it
        if (size == -1) {
            byte star = in.readByte();
            if (star == MARKER) {
                size = 0;
            } else {
                throw new CorruptedFrameException("Unexpected character in stream: " + star);
            }
        }
        if (size == 0) {
            // If the read fails, we need to skip the star
            size = -1;
            // Read the size, if this is successful we won't read the star again
            size = RedisReplyDecoder.readInteger(in);
            values = new Object[size];
            decoder.checkpoint();
        }
        for (int i = num; i < size; i++) {
            int read = in.readByte();
            if (read == BulkReply.MARKER) {
                values[i] = RedisReplyDecoder.readBytes(in);
            } else if (read == IntegerReply.MARKER) {
                values[i] = RedisReplyDecoder.readInteger(in);
            } else {
                throw new CorruptedFrameException("Unexpected character in stream: " + read);
            }
            num = i + 1;
            decoder.checkpoint();
        }
    }

    @Override
    void write(ChannelBuffer out) {
        out.writeByte(MARKER);
        if (values == null) {
            out.writeBytes(Command.NEG_ONE_AND_CRLF);
        } else {
            out.writeBytes(Command.numAndCRLF(values.length));
            for (Object value : values) {
                if (value == null) {
                    out.writeByte(BulkReply.MARKER);
                    out.writeBytes(Command.NEG_ONE_AND_CRLF);
                } else if (value instanceof ChannelBuffer) {
                    ChannelBuffer bytes = (ChannelBuffer) value;
                    out.writeByte(BulkReply.MARKER);
                    int length = bytes.readableBytes();
                    out.writeBytes(Command.numAndCRLF(length));
                    out.writeBytes(bytes, bytes.readerIndex(), length);
                    out.writeBytes(Command.CRLF);
                } else if (value instanceof Number) {
                    out.writeByte(IntegerReply.MARKER);
                    out.writeBytes(Command.numAndCRLF(((Number) value).longValue()));
                }
            }
        }
    }
}
