/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
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
    public void shouldNotifyImmediatelyOnAdd() throws Exception {
        ChannelFutureListener l = createStrictMock(ChannelFutureListener.class);
        l.operationComplete(future);
        replay(l);

        future.addListener(l);
        verify(l);
    }

    @Test
    public void shouldNotRethrowListenerException() {
        ChannelFutureListener l = new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future)
                    throws Exception {
                throw new ExpectedError();
            }
        };

        future.addListener(l);
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
        assertSame(channel, future.getChannel());
        assertTrue(future.isDone());
        assertFalse(future.cancel());
        assertFalse(future.isCancelled());
        assertSame(future, future.await());
        assertTrue(future.await(1));
        assertTrue(future.await(1, TimeUnit.NANOSECONDS));
        assertSame(future, future.awaitUninterruptibly());
        assertTrue(future.awaitUninterruptibly(1));
        assertTrue(future.awaitUninterruptibly(1, TimeUnit.NANOSECONDS));
    }

    private static class CompleteChannelFutureImpl extends CompleteChannelFuture {

        CompleteChannelFutureImpl(Channel channel) {
            super(channel);
        }

        public Throwable getCause() {
            throw new Error();
        }

        public boolean isSuccess() {
            throw new Error();
        }
    }

    private static class ExpectedError extends Error {
        private static final long serialVersionUID = 7059276744882005047L;

        ExpectedError() {
            super();
        }
    }
}
