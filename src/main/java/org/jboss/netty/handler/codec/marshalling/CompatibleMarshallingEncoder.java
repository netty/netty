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
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * {@link OneToOneEncoder} implementation which uses JBoss Marshalling to marshal
 * an Object.
 *
 * See <a href="http://www.jboss.org/jbossmarshalling">JBoss Marshalling website</a>
 * for more informations
 *
 * Use {@link MarshallingEncoder} if possible.
 *
 */
@Sharable
public class CompatibleMarshallingEncoder extends OneToOneEncoder {

    private final MarshallerProvider provider;

    /**
     * Create a new instance of the {@link CompatibleMarshallingEncoder}
     *
     * @param provider  the {@link MarshallerProvider} to use to get the {@link Marshaller} for a {@link Channel}
     */
    public CompatibleMarshallingEncoder(MarshallerProvider provider) {
        this.provider = provider;
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        Marshaller marshaller = provider.getMarshaller(ctx);
        ChannelBufferByteOutput output =
                new ChannelBufferByteOutput(ctx.getChannel().getConfig().getBufferFactory(), 256);
        marshaller.start(output);
        marshaller.writeObject(msg);
        marshaller.finish();
        marshaller.close();

        return output.getBuffer();
    }
}
