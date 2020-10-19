/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.marshalling;

import io.netty.channel.ChannelHandlerContext;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;

/**
 * Default implementation of {@link MarshallerProvider} which just create a new {@link Marshaller}
 * on ever {@link #getMarshaller(ChannelHandlerContext)} call.
 */
public class DefaultMarshallerProvider implements MarshallerProvider {

    private final MarshallerFactory factory;
    private final MarshallingConfiguration config;

    /**
     * Create a new instance
     *
     * @param factory   the {@link MarshallerFactory} to use to create {@link Marshaller}
     * @param config    the {@link MarshallingConfiguration}
     */
    public DefaultMarshallerProvider(MarshallerFactory factory, MarshallingConfiguration config) {
        this.factory = factory;
        this.config = config;
    }

    @Override
    public Marshaller getMarshaller(ChannelHandlerContext ctx) throws Exception {
        return factory.createMarshaller(config);
    }

}
