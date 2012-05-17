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
package io.netty.handler.codec.serialization;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferOutputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelOutboundHandlerContext;
import io.netty.handler.codec.MessageToStreamEncoder;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * An encoder which serializes a Java object into a {@link ChannelBuffer}.
 * <p>
 * Please note that the serialized form this encoder produces is not
 * compatible with the standard {@link ObjectInputStream}.  Please use
 * {@link ObjectDecoder} or {@link ObjectDecoderInputStream} to ensure the
 * interoperability with this encoder.
 * @apiviz.landmark
 * @apiviz.has io.netty.handler.codec.serialization.ObjectEncoderOutputStream - - - compatible with
 */
@Sharable
public class ObjectEncoder extends MessageToStreamEncoder<Object> {
    private static final byte[] LENGTH_PLACEHOLDER = new byte[4];

    private final int estimatedLength;

    /**
     * Creates a new encoder with the estimated length of 512 bytes.
     */
    public ObjectEncoder() {
        this(512);
    }

    /**
     * Creates a new encoder.
     *
     * @param estimatedLength
     *        the estimated byte length of the serialized form of an object.
     *        If the length of the serialized form exceeds this value, the
     *        internal buffer will be expanded automatically at the cost of
     *        memory bandwidth.  If this value is too big, it will also waste
     *        memory bandwidth.  To avoid unnecessary memory copy or allocation
     *        cost, please specify the properly estimated value.
     */
    public ObjectEncoder(int estimatedLength) {
        if (estimatedLength < 0) {
            throw new IllegalArgumentException(
                    "estimatedLength: " + estimatedLength);
        }
        this.estimatedLength = estimatedLength;
    }

    @Override
    public void encode(ChannelOutboundHandlerContext<Object> ctx, Object msg, ChannelBuffer out) throws Exception {
        int startIdx = out.writerIndex();

        ChannelBufferOutputStream bout = new ChannelBufferOutputStream(out);
        bout.write(LENGTH_PLACEHOLDER);
        ObjectOutputStream oout = new CompactObjectOutputStream(bout);
        oout.writeObject(msg);
        oout.flush();
        oout.close();

        int endIdx = out.writerIndex();

        out.setInt(startIdx, endIdx - startIdx - 4);
    }
}
