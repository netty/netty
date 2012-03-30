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
package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

public class SubscribeReply extends Reply {

    private final ChannelBuffer[] patterns;

    public SubscribeReply(byte[][] patterns) {
        this.patterns = new ChannelBuffer[patterns.length];
        for (int i = 0; i < patterns.length; i ++) {
            byte[] p = patterns[i];
            if (p == null) {
                continue;
            }
            this.patterns[i] = ChannelBuffers.wrappedBuffer(p);
        }
    }

    public SubscribeReply(ChannelBuffer[] patterns) {
        this.patterns = patterns;
    }

    public ChannelBuffer[] patterns() {
        return patterns;
    }

    @Override
    void write(ChannelBuffer out) {
        // Do nothing
    }
}
