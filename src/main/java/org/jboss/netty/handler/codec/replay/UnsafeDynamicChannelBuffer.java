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
package org.jboss.netty.handler.codec.replay;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.DynamicChannelBuffer;

/**
 * This class is not used by {@link ReplayingDecoder} anymore but is still here to not break API.
 * 
 * This class will get removed in the future.
 * 
 * @deprecated
 *
 */
@Deprecated
class UnsafeDynamicChannelBuffer extends DynamicChannelBuffer {

    UnsafeDynamicChannelBuffer(ChannelBufferFactory factory, int minimumCapacity) {
        super(factory.getDefaultOrder(), minimumCapacity, factory);
    }

    UnsafeDynamicChannelBuffer(ChannelBufferFactory factory) {
        this(factory, 256);
    }

    
    @Override
    protected void checkReadableBytes(int minReaderRemaining) {
        // Do not check here - ReplayingDecoderBuffer will check.
    }
}

