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

package io.netty.util.internal.logging;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class InternalLoggerFactoryTest {
    private static final Exception e = new Exception();
    private InternalLoggerFactory.InternalLoggerFactoryHolder holder;
    private InternalLogger mock;

    @Before
    public void init() {
        final InternalLoggerFactory mockFactory = createMock(InternalLoggerFactory.class);
        mock = createStrictMock(InternalLogger.class);
        expect(mockFactory.newInstance("mock")).andReturn(mock).anyTimes();
        replay(mockFactory);
        holder = new InternalLoggerFactory.InternalLoggerFactoryHolder(mockFactory);
    }

    @After
    public void destroy() {
        reset(mock);
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullDefaultFactory() {
        holder.setFactory(null);
    }

    @Test
    public void shouldGetInstance() {
        final String helloWorld = "Hello, world!";

        final InternalLogger one = InternalLoggerFactory.getInstance("helloWorld");
        final InternalLogger two = InternalLoggerFactory.getInstance(helloWorld.getClass());

        assertNotNull(one);
        assertNotNull(two);
        assertNotSame(one, two);
    }

    @Test
    public void testIsTraceEnabled() {
        expect(mock.isTraceEnabled()).andReturn(true);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isTraceEnabled());
        verify(mock);
    }

    @Test
    public void testIsDebugEnabled() {
        expect(mock.isDebugEnabled()).andReturn(true);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isDebugEnabled());
        verify(mock);
    }

    @Test
    public void testIsInfoEnabled() {
        expect(mock.isInfoEnabled()).andReturn(true);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isInfoEnabled());
        verify(mock);
    }

    @Test
    public void testIsWarnEnabled() {
        expect(mock.isWarnEnabled()).andReturn(true);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isWarnEnabled());
        verify(mock);
    }

    @Test
    public void testIsErrorEnabled() {
        expect(mock.isErrorEnabled()).andReturn(true);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isErrorEnabled());
        verify(mock);
    }

    @Test
    public void testTrace() {
        mock.trace("a");
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.trace("a");
        verify(mock);
    }

    @Test
    public void testTraceWithException() {
        mock.trace("a", e);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.trace("a", e);
        verify(mock);
    }

    @Test
    public void testDebug() {
        mock.debug("a");
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        mock.debug("a", e);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        mock.info("a");
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        mock.info("a", e);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        mock.warn("a");
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        mock.warn("a", e);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        mock.error("a");
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        mock.error("a", e);
        replay(mock);

        final InternalLogger logger = holder.getInstance("mock");
        logger.error("a", e);
        verify(mock);
    }

    @Test
    public void shouldNotAllowToSetFactoryTwice() {
        try {
            holder.setFactory(createMock(InternalLoggerFactory.class));
            fail("should have thrown IllegalStateException");
        } catch (final IllegalStateException e) {
            assertThat(e.getMessage(), containsString("factory is already set"));
        }

        try {
            final InternalLoggerFactory.InternalLoggerFactoryHolder implicit =
                    new InternalLoggerFactory.InternalLoggerFactoryHolder();
            implicit.getFactory(); // force initialization
            implicit.setFactory(createMock(InternalLoggerFactory.class));
            fail("should have thrown IllegalStateException");
        } catch (final IllegalStateException e) {
            assertThat(e.getMessage(), containsString("factory is already set"));
        }
    }

    @Test
    public void raceGetAndGet() throws BrokenBarrierException, InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final InternalLoggerFactory.InternalLoggerFactoryHolder holder =
                new InternalLoggerFactory.InternalLoggerFactoryHolder();
        final AtomicReference<InternalLoggerFactory> firstReference = new AtomicReference<InternalLoggerFactory>();
        final AtomicReference<InternalLoggerFactory> secondReference = new AtomicReference<InternalLoggerFactory>();

        final Thread firstGet = getThread(firstReference, holder, barrier);
        final Thread secondGet = getThread(secondReference, holder, barrier);

        firstGet.start();
        secondGet.start();
        // start the two get threads
        barrier.await();

        // wait for the two get threads to complete
        barrier.await();

        if (holder.getFactory() == firstReference.get()) {
            assertSame(holder.getFactory(), secondReference.get());
        } else if (holder.getFactory() == secondReference.get()) {
            assertSame(holder.getFactory(), firstReference.get());
        } else {
            fail("holder should have been set by one of the get threads");
        }
    }

    @Test
    public void raceGetAndSet() throws BrokenBarrierException, InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final InternalLoggerFactory.InternalLoggerFactoryHolder holder =
                new InternalLoggerFactory.InternalLoggerFactoryHolder();
        final InternalLoggerFactory internalLoggerFactory = createMock(InternalLoggerFactory.class);
        final AtomicReference<InternalLoggerFactory> reference = new AtomicReference<InternalLoggerFactory>();

        final Thread get = getThread(reference, holder, barrier);

        final AtomicBoolean setSuccess = new AtomicBoolean();
        final Thread set = setThread(internalLoggerFactory, holder, setSuccess, barrier);

        get.start();
        set.start();
        // start the get and set threads
        barrier.await();

        // wait for the get and set threads to complete
        barrier.await();

        if (setSuccess.get()) {
            assertSame(internalLoggerFactory, reference.get());
            assertSame(internalLoggerFactory, holder.getFactory());
        } else {
            assertNotSame(internalLoggerFactory, reference.get());
            assertNotSame(internalLoggerFactory, holder.getFactory());
            assertSame(holder.getFactory(), reference.get());
        }
    }

    @Test
    public void raceSetAndSet() throws BrokenBarrierException, InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final InternalLoggerFactory.InternalLoggerFactoryHolder holder =
                new InternalLoggerFactory.InternalLoggerFactoryHolder();
        final InternalLoggerFactory first = createMock(InternalLoggerFactory.class);
        final InternalLoggerFactory second = createMock(InternalLoggerFactory.class);

        final AtomicBoolean firstSetSuccess = new AtomicBoolean();
        final Thread firstSet = setThread(first, holder, firstSetSuccess, barrier);

        final AtomicBoolean secondSetSuccess = new AtomicBoolean();
        final Thread secondSet = setThread(second, holder, secondSetSuccess, barrier);

        firstSet.start();
        secondSet.start();
        // start the two set threads
        barrier.await();

        // wait for the two set threads to complete
        barrier.await();

        assertTrue(firstSetSuccess.get() || secondSetSuccess.get());
        if (firstSetSuccess.get()) {
            assertFalse(secondSetSuccess.get());
            assertSame(first, holder.getFactory());
        } else {
            assertFalse(firstSetSuccess.get());
            assertSame(second, holder.getFactory());
        }
    }

    private static Thread getThread(
            final AtomicReference<InternalLoggerFactory> reference,
            final InternalLoggerFactory.InternalLoggerFactoryHolder holder,
            final CyclicBarrier barrier) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                awaitUnchecked(barrier);
                reference.set(holder.getFactory());
                awaitUnchecked(barrier);
            }
        });
    }

    private static Thread setThread(
            final InternalLoggerFactory internalLoggerFactory,
            final InternalLoggerFactory.InternalLoggerFactoryHolder holder,
            final AtomicBoolean setSuccess,
            final CyclicBarrier barrier) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                awaitUnchecked(barrier);
                boolean success = true;
                try {
                    holder.setFactory(internalLoggerFactory);
                } catch (final IllegalStateException e) {
                    success = false;
                    assertThat(e.getMessage(), containsString("factory is already set"));
                } finally {
                    setSuccess.set(success);
                    awaitUnchecked(barrier);
                }
            }
        });
    }

    private static void awaitUnchecked(final CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (final InterruptedException exception) {
            throw new IllegalStateException(exception);
        } catch (final BrokenBarrierException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
