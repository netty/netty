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

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * {@link UnmarshallerProvider} which store a reference to the {@link Unmarshaller} in the
 * {@link ChannelHandlerContext} via the {@link ChannelHandlerContext#setAttachment(Object)}
 * method. So the same {@link Unmarshaller} will be used during the life-time of a {@link Channel}
 * for the {@link ChannelHandler}'s {@link ChannelHandlerContext}.
 *
 *
 */
public class ContextBoundUnmarshallerProvider extends DefaultUnmarshallerProvider {

    public ContextBoundUnmarshallerProvider(MarshallerFactory factory, MarshallingConfiguration config) {
        super(factory, config);
    }

    @Override
    public Unmarshaller getUnmarshaller(ChannelHandlerContext ctx) throws Exception {
        Unmarshaller unmarshaller = (Unmarshaller) ctx.getAttachment();
        if (unmarshaller == null) {
            unmarshaller = super.getUnmarshaller(ctx);
            ctx.setAttachment(unmarshaller);
        }
        return unmarshaller;
    }

}
