/*
 * Copyright 2023 The Netty Project
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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.util.concurrent.FastThreadLocal;

public class StubChannelInitializerExtension implements ChannelInitializerExtension {
    static final FastThreadLocal<Boolean> isApplicable = new FastThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() throws Exception {
            return Boolean.TRUE;
        }
    };
    static final FastThreadLocal<Channel> lastSeenClientChannel = new FastThreadLocal<Channel>();
    static final FastThreadLocal<Channel> lastSeenListenerChannel = new FastThreadLocal<Channel>();
    static final FastThreadLocal<Channel> lastSeenChildChannel = new FastThreadLocal<Channel>();

    public static void clearThreadLocals() {
        lastSeenChildChannel.remove();
        lastSeenClientChannel.remove();
        lastSeenListenerChannel.remove();
        isApplicable.set(Boolean.TRUE);
    }

    @Override
    public boolean isApplicable(ApplicableInfo info) {
        return isApplicable.get();
    }

    @Override
    public double priority() {
        return 0;
    }

    @Override
    public void postInitializeClientChannel(Channel channel) {
        lastSeenClientChannel.set(channel);
    }

    @Override
    public void postInitializeServerListenerChannel(Channel channel) {
        lastSeenListenerChannel.set(channel);
    }

    @Override
    public void postInitializeServerChildChannel(Channel channel) {
        lastSeenChildChannel.set(channel);
    }
}
