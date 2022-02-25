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
package io.netty5.util.internal.logging;

import io.netty5.util.internal.logging.InternalLoggerFactory.InternalLoggerFactoryHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InternalLoggerFactoryTest {
    private static final Exception e = new Exception();
    private InternalLoggerFactory.InternalLoggerFactoryHolder holder;
    private InternalLogger mockLogger;

    @BeforeEach
    public void init() {
        final InternalLoggerFactory mockFactory = mock(InternalLoggerFactory.class);
        mockLogger = mock(InternalLogger.class);
        when(mockFactory.newInstance("mock")).thenReturn(mockLogger);
        holder = new InternalLoggerFactoryHolder(mockFactory);
    }

    @AfterEach
    public void destroy() {
        reset(mockLogger);
    }

    @Test
    public void shouldNotAllowNullDefaultFactory() {
        assertThrows(NullPointerException.class, () -> InternalLoggerFactory.setDefaultFactory(null));
        assertThrows(NullPointerException.class, () -> holder.setFactory(null));
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

        InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isTraceEnabled());
        verify(mockLogger).isTraceEnabled();
    }

    @Test
    public void testIsDebugEnabled() {
        when(mockLogger.isDebugEnabled()).thenReturn(true);

        InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isDebugEnabled());
        verify(mockLogger).isDebugEnabled();
    }

    @Test
    public void testIsInfoEnabled() {
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isInfoEnabled());
        verify(mockLogger).isInfoEnabled();
    }

    @Test
    public void testIsWarnEnabled() {
        when(mockLogger.isWarnEnabled()).thenReturn(true);

        InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isWarnEnabled());
        verify(mockLogger).isWarnEnabled();
    }

    @Test
    public void testIsErrorEnabled() {
        when(mockLogger.isErrorEnabled()).thenReturn(true);

        InternalLogger logger = holder.getInstance("mock");
        assertTrue(logger.isErrorEnabled());
        verify(mockLogger).isErrorEnabled();
    }

    @Test
    public void testTrace() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.trace("a");
        verify(mockLogger).trace("a");
    }

    @Test
    public void testTraceWithException() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.trace("a", e);
        verify(mockLogger).trace("a", e);
    }

    @Test
    public void testDebug() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.debug("a");
        verify(mockLogger).debug("a");
    }

    @Test
    public void testDebugWithException() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.debug("a", e);
        verify(mockLogger).debug("a", e);
    }

    @Test
    public void testInfo() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.info("a");
        verify(mockLogger).info("a");
    }

    @Test
    public void testInfoWithException() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.info("a", e);
        verify(mockLogger).info("a", e);
    }

    @Test
    public void testWarn() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.warn("a");
        verify(mockLogger).warn("a");
    }

    @Test
    public void testWarnWithException() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.warn("a", e);
        verify(mockLogger).warn("a", e);
    }

    @Test
    public void testError() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.error("a");
        verify(mockLogger).error("a");
    }

    @Test
    public void testErrorWithException() {
        final InternalLogger logger = holder.getInstance("mock");
        logger.error("a", e);
        verify(mockLogger).error("a", e);
    }

    @Test
    public void shouldNotAllowToSetFactoryTwice() {
        var e = assertThrows(IllegalStateException.class, () -> holder.setFactory(mock(InternalLoggerFactory.class)));
        assertThat(e).hasMessageContaining("factory is already set");

        final InternalLoggerFactory.InternalLoggerFactoryHolder implicit =
                new InternalLoggerFactory.InternalLoggerFactoryHolder();
        implicit.getFactory(); // force initialization
        e = assertThrows(IllegalStateException.class, () -> implicit.setFactory(mock(InternalLoggerFactory.class)));
        assertThat(e).hasMessageContaining("factory is already set");
    }

    @Test
    public void raceGetAndGet() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final InternalLoggerFactory.InternalLoggerFactoryHolder holder =
                new InternalLoggerFactory.InternalLoggerFactoryHolder();
        final AtomicReference<InternalLoggerFactory> firstReference = new AtomicReference<>();
        final AtomicReference<InternalLoggerFactory> secondReference = new AtomicReference<>();

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
    public void raceGetAndSet() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final InternalLoggerFactory.InternalLoggerFactoryHolder holder =
                new InternalLoggerFactory.InternalLoggerFactoryHolder();
        final InternalLoggerFactory internalLoggerFactory = mock(InternalLoggerFactory.class);
        final AtomicReference<InternalLoggerFactory> reference = new AtomicReference<>();

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
    public void raceSetAndSet() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final InternalLoggerFactory.InternalLoggerFactoryHolder holder =
                new InternalLoggerFactory.InternalLoggerFactoryHolder();
        final InternalLoggerFactory first = mock(InternalLoggerFactory.class);
        final InternalLoggerFactory second = mock(InternalLoggerFactory.class);

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
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
