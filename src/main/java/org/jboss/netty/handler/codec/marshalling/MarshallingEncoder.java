/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.marshalling;

import org.jboss.marshalling.Marshaller;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * {@link OneToOneEncoder} implementation which uses JBoss Marshalling to marshal
 * an Object. Be aware that this {@link OneToOneEncoder} is not compatible with
 * an other client that just use JBoss Marshalling as it includes the size of every
 * {@link Object} that gets serialized in front of the {@link Object} itself.
 *
 * Use this with {@link MarshallingDecoder}
 *
 * See <a href="http://www.jboss.org/jbossmarshalling">JBoss Marshalling website</a>
 * for more informations
 *
 */
@Sharable
public class MarshallingEncoder extends OneToOneEncoder {
    private static final byte[] LENGTH_PLACEHOLDER = new byte[4];
    private final MarshallerProvider provider;

    private final int estimatedLength;

    /**
     * Creates a new encoder with the estimated length of 512 bytes.
     *
     * @param provider the {@link MarshallerProvider} to use
     */
    public MarshallingEncoder(MarshallerProvider provider) {
        this(provider, 512);
    }

    /**
     * Creates a new encoder.
     *
     * @param provider
     *        the {@link MarshallerProvider} to use
     * @param estimatedLength
     *        the estimated byte length of the serialized form of an object.
     *        If the length of the serialized form exceeds this value, the
     *        internal buffer will be expanded automatically at the cost of
     *        memory bandwidth.  If this value is too big, it will also waste
     *        memory bandwidth.  To avoid unnecessary memory copy or allocation
     *        cost, please specify the properly estimated value.
     */
    public MarshallingEncoder(MarshallerProvider provider, int estimatedLength) {
        if (estimatedLength < 0) {
            throw new IllegalArgumentException(
                    "estimatedLength: " + estimatedLength);
        }
        this.estimatedLength = estimatedLength;
        this.provider = provider;
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        Marshaller marshaller = provider.getMarshaller(ctx);
        ChannelBufferByteOutput output = new ChannelBufferByteOutput(
                ctx.getChannel().getConfig().getBufferFactory(), estimatedLength);
        output.getBuffer().writeBytes(LENGTH_PLACEHOLDER);
        marshaller.start(output);
        marshaller.writeObject(msg);
        marshaller.finish();
        marshaller.close();

        ChannelBuffer encoded = output.getBuffer();
        encoded.setInt(0, encoded.writerIndex() - 4);

        return encoded;
    }

}
