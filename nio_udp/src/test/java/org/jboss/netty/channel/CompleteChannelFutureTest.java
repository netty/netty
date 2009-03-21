/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
