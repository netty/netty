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
package io.netty.util.internal.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class InternalLoggerFactoryTest {
    private static final Exception e = new Exception();
    private InternalLoggerFactory oldLoggerFactory;
    private InternalLogger mockLogger;

    @BeforeEach
    public void init() {
        oldLoggerFactory = InternalLoggerFactory.getDefaultFactory();

        final InternalLoggerFactory mockFactory = mock(InternalLoggerFactory.class);
        mockLogger = mock(InternalLogger.class);
        when(mockFactory.newInstance("mock")).thenReturn(mockLogger);
        InternalLoggerFactory.setDefaultFactory(mockFactory);
    }

    @AfterEach
    public void destroy() {
        reset(mockLogger);
        InternalLoggerFactory.setDefaultFactory(oldLoggerFactory);
    }

    @Test
    public void shouldNotAllowNullDefaultFactory() {
        assertThrows(NullPointerException.class, () -> InternalLoggerFactory.setDefaultFactory(null));
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
        when(mockLogger.isTraceEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isTraceEnabled());
        verify(mockLogger).isTraceEnabled();
    }

    @Test
    public void testIsDebugEnabled() {
        when(mockLogger.isDebugEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isDebugEnabled());
        verify(mockLogger).isDebugEnabled();
    }

    @Test
    public void testIsInfoEnabled() {
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isInfoEnabled());
        verify(mockLogger).isInfoEnabled();
    }

    @Test
    public void testIsWarnEnabled() {
        when(mockLogger.isWarnEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isWarnEnabled());
        verify(mockLogger).isWarnEnabled();
    }

    @Test
    public void testIsErrorEnabled() {
        when(mockLogger.isErrorEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isErrorEnabled());
        verify(mockLogger).isErrorEnabled();
    }

    @Test
    public void testTrace() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.trace("a");
        verify(mockLogger).trace("a");
    }

    @Test
    public void testTraceWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.trace("a", e);
        verify(mockLogger).trace("a", e);
    }

    @Test
    public void testDebug() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a");
        verify(mockLogger).debug("a");
    }

    @Test
    public void testDebugWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a", e);
        verify(mockLogger).debug("a", e);
    }

    @Test
    public void testInfo() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a");
        verify(mockLogger).info("a");
    }

    @Test
    public void testInfoWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a", e);
        verify(mockLogger).info("a", e);
    }

    @Test
    public void testWarn() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a");
        verify(mockLogger).warn("a");
    }

    @Test
    public void testWarnWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a", e);
        verify(mockLogger).warn("a", e);
    }

    @Test
    public void testError() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a");
        verify(mockLogger).error("a");
    }

    @Test
    public void testErrorWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a", e);
        verify(mockLogger).error("a", e);
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
