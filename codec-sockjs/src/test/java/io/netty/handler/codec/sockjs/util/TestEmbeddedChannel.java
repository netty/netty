/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.util;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.sockjs.util.StubEmbeddedEventLoop.SchedulerExecutor;

/**
 * An EmbeddedChannel which can be used for testing. This implementation provides
 * a {@link SuccessSchedulerExecutor} as for schedule tasks.
 *
 * Note that we cannot pass the {@link SchedulerExecutor} instance into the
 * constructor of this class as the super classes constructor will call {@link #unsafe()}
 * and this will happen before the passed in instance would have been set. We therefore
 * need to override {@link #createTestUnsafe(AbstractUnsafe)} and return the {@link SchedulerExecutor}
 * implementation required.
 */
public class TestEmbeddedChannel extends AbstractTestEmbeddedChannel {

    public TestEmbeddedChannel(ChannelHandler... handlers) {
        super(handlers);
    }

    @Override
    protected AbstractTestUnsafe createTestUnsafe(AbstractUnsafe delegate) {
        return new AbstractTestUnsafe(delegate) {
            @Override
            public SchedulerExecutor createSchedulerExecutor() {
                return new SuccessSchedulerExecutor();
            }
        };
    }

}
