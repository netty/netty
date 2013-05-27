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
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * This class is intended to be used by test needing different handling
 * of scheduled tasks where {@link EmbeddedChannel} default implementation of
 * EmbeddedEventLoop throws {@link UnsupportedOperationException}.
 */
public abstract class AbstractTestEmbeddedChannel extends EmbeddedChannel {

    private AbstractTestUnsafe testUnsafe;

    protected AbstractTestEmbeddedChannel(final ChannelHandler... handlers) {
        super(handlers);
    }

    /**
     * Will be called when {@link #newUnsafe()} is invoked and allows concrete
     * implementations to create customized instances of {@link AbstractTestUnsafe}.
     */
    protected abstract AbstractTestUnsafe createTestUnsafe(AbstractUnsafe delegate);

    @Override
    protected AbstractUnsafe newUnsafe() {
        final AbstractUnsafe abstractUnsafe = super.newUnsafe();
        testUnsafe = createTestUnsafe(abstractUnsafe);
        return abstractUnsafe;
    }

    @Override
    public Unsafe unsafe() {
        return testUnsafe;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof StubEmbeddedEventLoop;
    }

}
