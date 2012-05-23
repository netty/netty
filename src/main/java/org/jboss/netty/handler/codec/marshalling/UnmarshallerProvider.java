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
package org.jboss.netty.handler.codec.marshalling;

import org.jboss.marshalling.Unmarshaller;
import org.jboss.netty.channel.Channel;

/**
 * This provider is responsible to get an {@link Unmarshaller} for a {@link Channel}
 *
 */
public interface UnmarshallerProvider {

    /**
     * Get the {@link Unmarshaller} for the given {@link Channel}
     */
    Unmarshaller getUnmarshaller(Channel channel) throws Exception;
}
