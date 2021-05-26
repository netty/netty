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
package io.netty.channel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompleteChannelFutureTest {

    @Test
    public void shouldDisallowNullChannel() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                new CompleteChannelFutureImpl(null);
            }
        });
    }

    @Test
    public void shouldNotDoAnythingOnRemove() {
        Channel channel = Mockito.mock(Channel.class);
        CompleteChannelFuture future = new CompleteChannelFutureImpl(channel);
        ChannelFutureListener l = Mockito.mock(ChannelFutureListener.class);
        future.removeListener(l);
        Mockito.verifyNoMoreInteractions(l);
        Mockito.verifyZeroInteractions(channel);
    }

    @Test
    public void testConstantProperties() throws InterruptedException {
        Channel channel = Mockito.mock(Channel.class);
        CompleteChannelFuture future = new CompleteChannelFutureImpl(channel);

        assertSame(channel, future.channel());
        assertTrue(future.isDone());
        assertSame(future, future.await());
        assertTrue(future.await(1));
        assertTrue(future.await(1, TimeUnit.NANOSECONDS));
        assertSame(future, future.awaitUninterruptibly());
        assertTrue(future.awaitUninterruptibly(1));
        assertTrue(future.awaitUninterruptibly(1, TimeUnit.NANOSECONDS));
        Mockito.verifyZeroInteractions(channel);
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
