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
package org.jboss.netty.handler.codec.spdy;

import java.nio.ByteOrder;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

public class SpdyHeaderBlockRawEncoder extends SpdyHeaderBlockEncoder {

    private final int version;

    public SpdyHeaderBlockRawEncoder(SpdyVersion spdyVersion) {
        if (spdyVersion == null) {
            throw new NullPointerException("spdyVersion");
        }
        version = spdyVersion.getVersion();
    }

    private void setLengthField(ChannelBuffer buffer, int writerIndex, int length) {
        buffer.setInt(writerIndex, length);
    }

    private void writeLengthField(ChannelBuffer buffer, int length) {
        buffer.writeInt(length);
    }

    @Override
    public ChannelBuffer encode(SpdyHeadersFrame headerFrame) throws Exception {
        Set<String> names = headerFrame.headers().names();
        int numHeaders = names.size();
        if (numHeaders == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        if (numHeaders > SPDY_MAX_NV_LENGTH) {
            throw new IllegalArgumentException(
                    "header block contains too many headers");
        }
        ChannelBuffer headerBlock = ChannelBuffers.dynamicBuffer(
                ByteOrder.BIG_ENDIAN, 256);
        writeLengthField(headerBlock, numHeaders);
        for (String name: names) {
            byte[] nameBytes = name.getBytes("UTF-8");
            writeLengthField(headerBlock, nameBytes.length);
            headerBlock.writeBytes(nameBytes);
            int savedIndex = headerBlock.writerIndex();
            int valueLength = 0;
            writeLengthField(headerBlock, valueLength);
            for (String value: headerFrame.headers().getAll(name)) {
                byte[] valueBytes = value.getBytes("UTF-8");
                if (valueBytes.length > 0) {
                    headerBlock.writeBytes(valueBytes);
                    headerBlock.writeByte(0);
                    valueLength += valueBytes.length + 1;
                }
            }
            if (valueLength != 0) {
                valueLength --;
            }
            if (valueLength > SPDY_MAX_NV_LENGTH) {
                throw new IllegalArgumentException(
                        "header exceeds allowable length: " + name);
            }
            if (valueLength > 0) {
                setLengthField(headerBlock, savedIndex, valueLength);
                headerBlock.writerIndex(headerBlock.writerIndex() - 1);
            }
        }
        return headerBlock;
    }

    @Override
    void end() {
    }
}
