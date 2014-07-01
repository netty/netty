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
package io.netty.handler.codec.marshalling;

import io.netty.channel.ChannelHandlerContext;

import io.netty.util.concurrent.FastThreadLocal;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;

/**
 * {@link UnmarshallerProvider} implementation which use a {@link ThreadLocal} to store references
 * to {@link Marshaller} instances. This may give you some performance boost if you need to marshall
 * many small {@link Object}'s and your actual Thread count is not to big
 */
public class ThreadLocalMarshallerProvider implements MarshallerProvider {
    private final FastThreadLocal<Marshaller> marshallers = new FastThreadLocal<Marshaller>();

    private final MarshallerFactory factory;
    private final MarshallingConfiguration config;

    /**
     * Create a new instance of the {@link ThreadLocalMarshallerProvider}
     *
     * @param factory   the {@link MarshallerFactory} to use to create {@link Marshaller}'s if needed
     * @param config    the {@link MarshallingConfiguration} to use
     */
    public ThreadLocalMarshallerProvider(MarshallerFactory factory, MarshallingConfiguration config) {
        this.factory = factory;
        this.config = config;
    }

    @Override
    public Marshaller getMarshaller(ChannelHandlerContext ctx) throws Exception {
        Marshaller marshaller = marshallers.get();
        if (marshaller == null) {
            marshaller = factory.createMarshaller(config);
            marshallers.set(marshaller);
        }
        return marshaller;
    }
}
