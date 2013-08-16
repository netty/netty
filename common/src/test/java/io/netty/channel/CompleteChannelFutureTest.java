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
package io.netty.channel;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

public class CompleteChannelFutureTest {

    private final Channel channel = createMock(Channel.class);
    private CompleteChannelFuture future;

    @Before
    public void init() {
        future = new CompleteChannelFutureImpl(channel);
    }

    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullChannel() {
        new CompleteChannelFutureImpl(null);
    }

    @Test
    public void shouldNotDoAnythingOnRemove() throws Exception {
        ChannelFutureListener l = createStrictMock(ChannelFutureListener.class);
        replay(l);

        future.removeListener(l);
        verify(l);
    }

    @Test
    public void testConstantProperties() throws InterruptedException {
        assertSame(channel, future.channel());
        assertTrue(future.isDone());
        assertSame(future, future.await());
        assertTrue(future.await(1));
        assertTrue(future.await(1, TimeUnit.NANOSECONDS));
        assertSame(future, future.awaitUninterruptibly());
        assertTrue(future.awaitUninterruptibly(1));
        assertTrue(future.awaitUninterruptibly(1, TimeUnit.NANOSECONDS));
    }

    private static class CompleteChannelFutureImpl extends CompleteChannelFuture {

        CompleteChannelFutureImpl(Channel channel) {
            super(channel, null);
        }

        @Override
        public Throwable cause() {
            throw new Error();
        }

        @Override
        public boolean isSuccess() {
            throw new Error();
        }

        @Override
        public ChannelFuture sync() throws InterruptedException {
            throw new Error();
        }

        @Override
        public ChannelFuture syncUninterruptibly() {
            throw new Error();
        }
    }
}
