/*
 * Copyright 2017 The Netty Project
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

package io.netty.util;

import io.netty.util.internal.SystemPropertyUtil;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class NettyRuntimeTests {

    @Test
    public void testIllegalSet() {
        final NettyRuntime.AvailableProcessorsHolder holder = new NettyRuntime.AvailableProcessorsHolder();
        for (final int i : new int[] { -1, 0 }) {
            try {
                holder.setAvailableProcessors(i);
                fail();
            } catch (final IllegalArgumentException e) {
                assertThat(e, hasToString(containsString("(expected: > 0)")));
            }
        }
    }

    @Test
    public void testMultipleSets() {
        final NettyRuntime.AvailableProcessorsHolder holder = new NettyRuntime.AvailableProcessorsHolder();
        holder.setAvailableProcessors(1);
        try {
            holder.setAvailableProcessors(2);
            fail();
        } catch (final IllegalStateException e) {
            assertThat(e, hasToString(containsString("availableProcessors is already set to [1], rejecting [2]")));
        }
    }

    @Test
    public void testSetAfterGet() {
        final NettyRuntime.AvailableProcessorsHolder holder = new NettyRuntime.AvailableProcessorsHolder();
        holder.availableProcessors();
        try {
            holder.setAvailableProcessors(1);
            fail();
        } catch (final IllegalStateException e) {
            assertThat(e, hasToString(containsString("availableProcessors is already set")));
        }
    }

    @Test
    public void testRacingGetAndGet() throws InterruptedException {
        final NettyRuntime.AvailableProcessorsHolder holder = new NettyRuntime.AvailableProcessorsHolder();
        final CyclicBarrier barrier = new CyclicBarrier(3);

        final AtomicReference<IllegalStateException> firstReference = new AtomicReference<IllegalStateException>();
        final Runnable firstTarget = getRunnable(holder, barrier, firstReference);
        final Thread firstGet = new Thread(firstTarget);
        firstGet.start();

        final AtomicReference<IllegalStateException> secondRefernce = new AtomicReference<IllegalStateException>();
        final Runnable secondTarget = getRunnable(holder, barrier, secondRefernce);
        final Thread secondGet = new Thread(secondTarget);
        secondGet.start();

        // release the hounds
        await(barrier);

        // wait for the hounds
        await(barrier);

        firstGet.join();
        secondGet.join();

        assertNull(firstReference.get());
        assertNull(secondRefernce.get());
    }

    private static Runnable getRunnable(
            final NettyRuntime.AvailableProcessorsHolder holder,
            final CyclicBarrier barrier,
            final AtomicReference<IllegalStateException> reference) {
        return new Runnable() {
            @Override
            public void run() {
                await(barrier);
                try {
                    holder.availableProcessors();
                } catch (final IllegalStateException e) {
                    reference.set(e);
                }
                await(barrier);
            }
        };
    }

    @Test
    public void testRacingGetAndSet() throws InterruptedException {
        final NettyRuntime.AvailableProcessorsHolder holder = new NettyRuntime.AvailableProcessorsHolder();
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final Thread get = new Thread(new Runnable() {
            @Override
            public void run() {
                await(barrier);
                holder.availableProcessors();
                await(barrier);
            }
        });
        get.start();

        final AtomicReference<IllegalStateException> setException = new AtomicReference<IllegalStateException>();
        final Thread set = new Thread(new Runnable() {
            @Override
            public void run() {
                await(barrier);
                try {
                    holder.setAvailableProcessors(2048);
                } catch (final IllegalStateException e) {
                    setException.set(e);
                }
                await(barrier);
            }
        });
        set.start();

        // release the hounds
        await(barrier);

        // wait for the hounds
        await(barrier);

        get.join();
        set.join();

        if (setException.get() == null) {
            assertThat(holder.availableProcessors(), equalTo(2048));
        } else {
            assertNotNull(setException.get());
        }
    }

    @Test
    public void testGetWithSystemProperty() {
        final String availableProcessorsSystemProperty = SystemPropertyUtil.get("io.netty.availableProcessors");
        try {
            System.setProperty("io.netty.availableProcessors", "2048");
            final NettyRuntime.AvailableProcessorsHolder holder = new NettyRuntime.AvailableProcessorsHolder();
            assertThat(holder.availableProcessors(), equalTo(2048));
        } finally {
            if (availableProcessorsSystemProperty != null) {
                System.setProperty("io.netty.availableProcessors", availableProcessorsSystemProperty);
            } else {
                System.clearProperty("io.netty.availableProcessors");
            }
        }
    }

    @Test
    @SuppressForbidden(reason = "testing fallback to Runtime#availableProcessors")
    public void testGet() {
        final String availableProcessorsSystemProperty = SystemPropertyUtil.get("io.netty.availableProcessors");
        try {
            System.clearProperty("io.netty.availableProcessors");
            final NettyRuntime.AvailableProcessorsHolder holder = new NettyRuntime.AvailableProcessorsHolder();
            assertThat(holder.availableProcessors(), equalTo(Runtime.getRuntime().availableProcessors()));
        } finally {
            if (availableProcessorsSystemProperty != null) {
                System.setProperty("io.netty.availableProcessors", availableProcessorsSystemProperty);
            } else {
                System.clearProperty("io.netty.availableProcessors");
            }
        }
    }

    private static void await(final CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (final InterruptedException e) {
            fail(e.toString());
        } catch (final BrokenBarrierException e) {
            fail(e.toString());
        }
    }
}
