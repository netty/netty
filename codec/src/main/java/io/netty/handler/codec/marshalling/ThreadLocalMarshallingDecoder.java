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

import java.io.IOException;

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import io.netty.channel.Channel;

/**
 * A subclass of {@link MarshallingDecoder} which use one {@link Unmarshaller} per Thread via a {@link ThreadLocal}.
 * 
 * For more informations see {@link MarshallingDecoder}.
 *
 */
public class ThreadLocalMarshallingDecoder extends MarshallingDecoder {

    private static final ThreadLocal<Unmarshaller> UNMARSHALLERS = new ThreadLocal<Unmarshaller>();
    
    /**
     * See {@link MarshallingDecoder#MarshallingDecoder(MarshallerFactory, MarshallingConfiguration, long)}
     */
    public ThreadLocalMarshallingDecoder(MarshallerFactory factory, MarshallingConfiguration config, long maxObjectSize) {
        super(factory, config, maxObjectSize);
    }

    @Override
    protected Unmarshaller getUnmarshaller(Channel channel) throws IOException {
        Unmarshaller unmarshaller = UNMARSHALLERS.get();
        if (unmarshaller == null) {
            unmarshaller = factory.createUnmarshaller(config);
            UNMARSHALLERS.set(unmarshaller);
        }
        return unmarshaller;
    }

}
