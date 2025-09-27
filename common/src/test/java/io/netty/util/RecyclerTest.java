/*
* Copyright 2014 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.MathUtil;
import org.assertj.core.api.Assumptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class RecyclerTest {

    public enum OwnerType {
        NONE,
        PINNED,
        FAST_THREAD_LOCAL,
    }

    public static Stream<Arguments> ownerTypeAndUnguarded() {
        return Arrays.stream(OwnerType.values())
                     .flatMap(owner -> Stream.of(true, false)
                                             .map(unguarded -> Arguments.of(owner, unguarded)));
    }

    protected static Recycler<HandledObject> newRecycler(OwnerType ownerType, boolean unguarded,
                                                         int maxCapacityPerThread) {
        return newRecycler(ownerType, unguarded, maxCapacityPerThread, null);
    }

    protected static Recycler<HandledObject> newRecycler(OwnerType ownerType, boolean unguarded,
                                                         int maxCapacityPerThread,
                                                         Consumer<HandledObject> onNewObject) {
        switch (ownerType) {
        case NONE:
            return new Recycler<HandledObject>(maxCapacityPerThread, unguarded) {
                @Override
                protected HandledObject newObject(Handle<HandledObject> handle) {
                    HandledObject newObj = new HandledObject(handle);
                    if (onNewObject != null) {
                        onNewObject.accept(newObj);
                    }
                    return newObj;
                }
            };
        case PINNED:
            return new Recycler<HandledObject>(maxCapacityPerThread >> 1, maxCapacityPerThread, Thread.currentThread(),
                                               unguarded) {
                @Override
                protected HandledObject newObject(
                        Recycler.Handle<HandledObject> handle) {
                    HandledObject newObj = new HandledObject(handle);
                    if (onNewObject != null) {
                        onNewObject.accept(newObj);
                    }
                    return newObj;
                }
            };
        case FAST_THREAD_LOCAL:
            return new Recycler<HandledObject>(maxCapacityPerThread >> 1, maxCapacityPerThread, unguarded) {
                @Override
                protected HandledObject newObject(Handle<HandledObject> handle) {
                    HandledObject newObj = new HandledObject(handle);
                    if (onNewObject != null) {
                        onNewObject.accept(newObj);
                    }
                    return newObj;
                }
            };
        default:
            throw new Error();
        }
    }

    protected static Recycler<HandledObject> newRecycler(boolean unguarded, int maxCapacityPerThread) {
        return new Recycler<HandledObject>(maxCapacityPerThread >> 1, maxCapacityPerThread, unguarded) {
            @Override
            protected HandledObject newObject(
                    Recycler.Handle<HandledObject> handle) {
                return new HandledObject(handle);
            }
        };
    }

    protected static Recycler<HandledObject> newRecycler(int maxCapacityPerThread) {
        return newRecycler(OwnerType.FAST_THREAD_LOCAL, false, maxCapacityPerThread, 8, maxCapacityPerThread >> 1);
    }

    protected static Recycler<HandledObject> newRecycler(OwnerType ownerType, boolean unguarded,
                                                         int maxCapacityPerThread, int ratio, int chunkSize) {
        // NOTE: ration and chunk size will be ignored for NONE owner type!
        switch (ownerType) {
        case NONE:
            return new Recycler<HandledObject>(maxCapacityPerThread, unguarded) {
                @Override
                protected HandledObject newObject(Handle<HandledObject> handle) {
                    return new HandledObject(handle);
                }
            };
        case PINNED:
            return new Recycler<HandledObject>(maxCapacityPerThread, ratio, chunkSize, Thread.currentThread(),
                                               unguarded) {
                @Override
                protected HandledObject newObject(
                        Recycler.Handle<HandledObject> handle) {
                    return new HandledObject(handle);
                }
            };
        case FAST_THREAD_LOCAL:
            return new Recycler<HandledObject>(maxCapacityPerThread, ratio, chunkSize, unguarded) {
                @Override
                protected HandledObject newObject(
                        Recycler.Handle<HandledObject> handle) {
                    return new HandledObject(handle);
                }
            };
        default:
            throw new Error();
        }
    }

    @NotNull
    protected Thread newThread(Runnable runnable) {
        return new Thread(runnable);
    }

    @ParameterizedTest
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    @MethodSource("ownerTypeAndUnguarded")
    public void testThreadCanBeCollectedEvenIfHandledObjectIsReferenced(OwnerType ownerType, boolean unguarded)
            throws Exception {
        final AtomicBoolean collected = new AtomicBoolean();
        final AtomicReference<HandledObject> reference = new AtomicReference<HandledObject>();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024);
                HandledObject object = recycler.get();
                // Store a reference to the HandledObject to ensure it is not collected when the run method finish.
                reference.set(object);
                Recycler.unpinOwner(recycler);
            }
        }) {
            @Override
            protected void finalize() throws Throwable {
                super.finalize();
                collected.set(true);
            }
        };
        assertFalse(collected.get());
        thread.start();
        thread.join();

        // Null out so it can be collected.
        thread = null;

        // Loop until the Thread was collected. If we can not collect it the Test will fail due of a timeout.
        while (!collected.get()) {
            System.gc();
            System.runFinalization();
            Thread.sleep(50);
        }

        // Now call recycle after the Thread was collected to ensure this still works...
        if (reference.get() != null) {
            reference.getAndSet(null).recycle();
        }
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void verySmallRecycer(OwnerType ownerType, boolean unguarded) {
        newRecycler(ownerType, unguarded, 2, 0, 1).get();
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testMultipleRecycle(OwnerType ownerType, boolean unguarded) {
        assumeFalse(unguarded);
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024);
        final HandledObject object = recycler.get();
        object.recycle();
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                object.recycle();
            }
        });
    }

    @Test
    public void testUnguardedMultipleRecycle() {
        Recycler<HandledObject> recycler = newRecycler(true, 1024);
        final HandledObject object = recycler.get();
        object.recycle();
        object.recycle();
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testMultipleRecycleAtDifferentThread(OwnerType ownerType, boolean unguarded)
            throws InterruptedException {
        assumeFalse(unguarded, "This test makes only sense for guarded recyclers");
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024);
        final HandledObject object = recycler.get();
        final AtomicReference<IllegalStateException> exceptionStore = new AtomicReference<IllegalStateException>();
        final Thread thread1 = newThread(new Runnable() {
            @Override
            public void run() {
                object.recycle();
            }
        });
        thread1.start();
        thread1.join();

        final Thread thread2 = newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    object.recycle();
                } catch (IllegalStateException e) {
                    exceptionStore.set(e);
                }
            }
        });
        thread2.start();
        thread2.join();
        HandledObject a = recycler.get();
        HandledObject b = recycler.get();
        assertNotSame(a, b);
        IllegalStateException exception = exceptionStore.get();
        assertNotNull(exception);
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testMultipleRecycleAtDifferentThreadRacing(OwnerType ownerType, boolean unguarded)
            throws InterruptedException {
        assumeFalse(unguarded, "This test makes only sense for guarded recyclers");
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024);
        final HandledObject object = recycler.get();
        final AtomicReference<IllegalStateException> exceptionStore = new AtomicReference<IllegalStateException>();

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final Thread thread1 = newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    object.recycle();
                } catch (IllegalStateException e) {
                    Exception x = exceptionStore.getAndSet(e);
                    if (x != null) {
                        e.addSuppressed(x);
                    }
                } finally {
                    countDownLatch.countDown();
                }
            }
        });
        thread1.start();

        final Thread thread2 = newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    object.recycle();
                } catch (IllegalStateException e) {
                    Exception x = exceptionStore.getAndSet(e);
                    if (x != null) {
                        e.addSuppressed(x);
                    }
                } finally {
                    countDownLatch.countDown();
                }
            }
        });
        thread2.start();

        try {
            countDownLatch.await();
            HandledObject a = recycler.get();
            HandledObject b = recycler.get();
            assertNotSame(a, b);
            IllegalStateException exception = exceptionStore.get();
            if (exception != null) {
                assertThat(exception).hasMessageContaining("recycled already");
                assertEquals(0, exception.getSuppressed().length);
            }
        } finally {
            thread1.join(1000);
            thread2.join(1000);
        }
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testMultipleRecycleRacing(OwnerType ownerType, boolean unguarded) throws InterruptedException {
        assumeFalse(unguarded, "This test makes only sense for guarded recyclers");
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024);
        final HandledObject object = recycler.get();
        final AtomicReference<IllegalStateException> exceptionStore = new AtomicReference<IllegalStateException>();

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Thread thread1 = newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    object.recycle();
                } catch (IllegalStateException e) {
                    Exception x = exceptionStore.getAndSet(e);
                    if (x != null) {
                        e.addSuppressed(x);
                    }
                } finally {
                    countDownLatch.countDown();
                }
            }
        });
        thread1.start();

        try {
            object.recycle();
        } catch (IllegalStateException e) {
            Exception x = exceptionStore.getAndSet(e);
            if (x != null) {
                e.addSuppressed(x);
            }
        }

        try {
            countDownLatch.await();
            HandledObject a = recycler.get();
            HandledObject b = recycler.get();
            assertNotSame(a, b);
            IllegalStateException exception = exceptionStore.get();
            assertNotNull(exception); // Object got recycled twice, so at least one of the calls must throw.
        } finally {
            thread1.join(1000);
        }
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testRecycle(OwnerType ownerType, boolean unguarded) {
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024);
        HandledObject object = recycler.get();
        object.recycle();
        HandledObject object2 = recycler.get();
        assertSame(object, object2);
        object2.recycle();
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testRecycleDisable(OwnerType ownerType, boolean unguarded) {
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, -1);
        HandledObject object = recycler.get();
        object.recycle();
        HandledObject object2 = recycler.get();
        assertNotSame(object, object2);
        object2.recycle();
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testRecycleDisableDrop(OwnerType ownerType, boolean unguarded) {
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024, 0, 16);
        HandledObject object = recycler.get();
        object.recycle();
        HandledObject object2 = recycler.get();
        assertSame(object, object2);
        object2.recycle();
        HandledObject object3 = recycler.get();
        assertSame(object, object3);
        object3.recycle();
    }

    /**
     * Test to make sure bug #2848 never happens again
     * https://github.com/netty/netty/issues/2848
     */
    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testMaxCapacity(OwnerType ownerType, boolean unguarded) {
        testMaxCapacity(ownerType, unguarded, 300);
        Random rand = new Random();
        for (int i = 0; i < 50; i++) {
            testMaxCapacity(ownerType, unguarded, rand.nextInt(1000) + 256); // 256 - 1256
        }
    }

    private static void testMaxCapacity(OwnerType ownerType, boolean unguarded, int maxCapacity) {
        Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, maxCapacity);
        HandledObject[] objects = new HandledObject[maxCapacity * 3];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = recycler.get();
        }

        for (int i = 0; i < objects.length; i++) {
            objects[i].recycle();
            objects[i] = null;
        }

        assertTrue(MathUtil.findNextPositivePowerOfTwo(maxCapacity) >= recycler.threadLocalSize(),
                "The threadLocalSize (" + recycler.threadLocalSize() + ") must be <= maxCapacity ("
                + maxCapacity + ") as we not pool all new handles internally");
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testRecycleAtDifferentThread(OwnerType ownerType, boolean unguarded) throws Exception {
        assumeThat(ownerType).isNotEqualTo(OwnerType.NONE);
        final Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 256, 2, 16);
        final HandledObject o = recycler.get();
        final HandledObject o2 = recycler.get();

        final Thread thread = newThread(new Runnable() {
            @Override
            public void run() {
                o.recycle();
                o2.recycle();
            }
        });
        thread.start();
        thread.join();

        assertSame(recycler.get(), o);
        assertNotSame(recycler.get(), o2);
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testRecycleAtTwoThreadsMulti(OwnerType ownerType, boolean unguarded) throws Exception {
        final Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 256);
        final HandledObject o = recycler.get();

        ExecutorService single = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@NotNull Runnable r) {
                return RecyclerTest.this.newThread(r);
            }
        });

        final CountDownLatch latch1 = new CountDownLatch(1);
        single.execute(new Runnable() {
            @Override
            public void run() {
                o.recycle();
                latch1.countDown();
            }
        });
        assertTrue(latch1.await(100, TimeUnit.MILLISECONDS));
        final HandledObject o2 = recycler.get();
        // Always recycler the first object, that is Ok
        assertSame(o2, o);

        final CountDownLatch latch2 = new CountDownLatch(1);
        single.execute(new Runnable() {
            @Override
            public void run() {
                //The object should be recycled
                o2.recycle();
                latch2.countDown();
            }
        });
        assertTrue(latch2.await(100, TimeUnit.MILLISECONDS));

        // It should be the same object, right?
        final HandledObject o3 = recycler.get();
        assertSame(o3, o);
        single.shutdown();
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testMaxCapacityWithRecycleAtDifferentThread(OwnerType ownerType, boolean unguarded) throws Exception {
        assumeThat(ownerType).isNotEqualTo(OwnerType.NONE);
        final int maxCapacity = 4;
        final Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, maxCapacity, 4, 4);

        // Borrow 2 * maxCapacity objects.
        // Return the half from the same thread.
        // Return the other half from the different thread.

        final HandledObject[] array = new HandledObject[maxCapacity * 3];
        for (int i = 0; i < array.length; i ++) {
            array[i] = recycler.get();
        }

        for (int i = 0; i < maxCapacity; i ++) {
            array[i].recycle();
        }

        final Thread thread = newThread(new Runnable() {
            @Override
            public void run() {
                for (int i1 = maxCapacity; i1 < array.length; i1++) {
                    array[i1].recycle();
                }
            }
        });
        thread.start();
        thread.join();

        assertEquals(maxCapacity * 3 / 4, recycler.threadLocalSize());

        for (int i = 0; i < array.length; i ++) {
            recycler.get();
        }

        assertEquals(0, recycler.threadLocalSize());
    }

    @ParameterizedTest
    @MethodSource("ownerTypeAndUnguarded")
    public void testDiscardingExceedingElementsWithRecycleAtDifferentThread(OwnerType ownerType, boolean unguarded)
            throws Exception {
        assumeThat(ownerType).isNotEqualTo(OwnerType.NONE);
        final int maxCapacity = 32;
        final AtomicInteger instancesCount = new AtomicInteger(0);

        final Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, maxCapacity, ignore ->
                instancesCount.incrementAndGet());

        // Borrow 2 * maxCapacity objects.
        final HandledObject[] array = new HandledObject[maxCapacity * 2];
        for (int i = 0; i < array.length; i++) {
            array[i] = recycler.get();
        }

        assertEquals(array.length, instancesCount.get());
        // Reset counter.
        instancesCount.set(0);

        // Recycle from other thread.
        final Thread thread = newThread(new Runnable() {
            @Override
            public void run() {
                for (HandledObject object: array) {
                    object.recycle();
                }
            }
        });
        thread.start();
        thread.join();

        assertEquals(0, instancesCount.get());

        // Borrow 2 * maxCapacity objects. Half of them should come from
        // the recycler queue, the other half should be freshly allocated.
        for (int i = 0; i < array.length; i++) {
            recycler.get();
        }

        // The implementation uses maxCapacity / 2 as limit per WeakOrderQueue
        assertTrue(array.length - maxCapacity / 2 <= instancesCount.get(),
                "The instances count (" +  instancesCount.get() + ") must be <= array.length (" + array.length
                + ") - maxCapacity (" + maxCapacity + ") / 2 as we not pool all new handles" +
                " internally");
    }

    static final class HandledObject {
        Recycler.Handle<HandledObject> handle;

        HandledObject(Recycler.Handle<HandledObject> handle) {
            this.handle = handle;
        }

        void recycle() {
            handle.recycle(this);
        }
    }
}
