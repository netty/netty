/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.marshalling;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * {@link OneToOneEncoder} implementation which uses JBoss Marshalling to marshal 
 * an Object.
 * 
 * See <a href="http://www.jboss.org/jbossmarshalling">JBoss Marshalling website</a> 
 * for more informations
 *
 */
@Sharable
public class MarshallingEncoder extends OneToOneEncoder {

    private final MarshallerFactory factory;
    private final MarshallingConfiguration config;


    public MarshallingEncoder(MarshallerFactory factory, MarshallingConfiguration config) {
        this.factory = factory;
        this.config = config;
    }
    
    
    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        Marshaller marshaller = factory.createMarshaller(config);
        ChannelBufferByteOutput output = new ChannelBufferByteOutput(ctx.getChannel().getConfig().getBufferFactory(), 256);
        marshaller.start(output);
        marshaller.writeObject(msg);
        marshaller.finish();
        marshaller.close();
        
        return output.getBuffer();
    }

}
