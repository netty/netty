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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecyclerTest {

    private static Recycler<HandledObject> newRecycler(int maxCapacityPerThread) {
        return newRecycler(maxCapacityPerThread, 2, 8, 2, 8);
    }

    private static Recycler<HandledObject> newRecycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                                                       int ratio, int maxDelayedQueuesPerThread,
                                                       int delayedQueueRatio) {
        return new Recycler<HandledObject>(maxCapacityPerThread, maxSharedCapacityFactor, ratio,
                maxDelayedQueuesPerThread, delayedQueueRatio) {
            @Override
            protected HandledObject newObject(
                    Recycler.Handle<HandledObject> handle) {
                return new HandledObject(handle);
            }
        };
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testThreadCanBeCollectedEvenIfHandledObjectIsReferenced() throws Exception {
        final Recycler<HandledObject> recycler = newRecycler(1024);
        final AtomicBoolean collected = new AtomicBoolean();
        final AtomicReference<HandledObject> reference = new AtomicReference<HandledObject>();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                HandledObject object = recycler.get();
                // Store a reference to the HandledObject to ensure it is not collected when the run method finish.
                reference.set(object);
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
        reference.getAndSet(null).recycle();
    }

    @Test
    public void testMultipleRecycle() {
        Recycler<HandledObject> recycler = newRecycler(1024);
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
    public void testMultipleRecycleAtDifferentThread() throws InterruptedException {
        Recycler<HandledObject> recycler = newRecycler(1024);
        final HandledObject object = recycler.get();
        final AtomicReference<IllegalStateException> exceptionStore = new AtomicReference<IllegalStateException>();
        final Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                object.recycle();
            }
        });
        thread1.start();
        thread1.join();

        final Thread thread2 = new Thread(new Runnable() {
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

    @Test
    public void testMultipleRecycleAtDifferentThreadRacing() throws InterruptedException {
        Recycler<HandledObject> recycler = newRecycler(1024);
        final HandledObject object = recycler.get();
        final AtomicReference<IllegalStateException> exceptionStore = new AtomicReference<IllegalStateException>();

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final Thread thread1 = new Thread(new Runnable() {
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

        final Thread thread2 = new Thread(new Runnable() {
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
                assertEquals("recycled already", exception.getMessage());
                assertEquals(0, exception.getSuppressed().length);
            }
        } finally {
            thread1.join(1000);
            thread2.join(1000);
        }
    }

    @Test
    public void testMultipleRecycleRacing() throws InterruptedException {
        Recycler<HandledObject> recycler = newRecycler(1024);
        final HandledObject object = recycler.get();
        final AtomicReference<IllegalStateException> exceptionStore = new AtomicReference<IllegalStateException>();

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Thread thread1 = new Thread(new Runnable() {
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
            if (exception != null) {
                throw exception;
            }
        } finally {
            thread1.join(1000);
        }
    }

    @Test
    public void testRecycle() {
        Recycler<HandledObject> recycler = newRecycler(1024);
        HandledObject object = recycler.get();
        object.recycle();
        HandledObject object2 = recycler.get();
        assertSame(object, object2);
        object2.recycle();
    }

    @Test
    public void testRecycleDisable() {
        Recycler<HandledObject> recycler = newRecycler(-1);
        HandledObject object = recycler.get();
        object.recycle();
        HandledObject object2 = recycler.get();
        assertNotSame(object, object2);
        object2.recycle();
    }

    @Test
    public void testRecycleDisableDrop() {
        Recycler<HandledObject> recycler = newRecycler(1024, 2, 0, 2, 0);
        HandledObject object = recycler.get();
        object.recycle();
        HandledObject object2 = recycler.get();
        assertSame(object, object2);
        object2.recycle();
        HandledObject object3 = recycler.get();
        assertSame(object, object3);
        object3.recycle();
    }

    @Test
    public void testRecycleDisableDelayedQueueDrop() throws Exception {
        final Recycler<HandledObject> recycler = newRecycler(1024, 2, 1, 2, 0);
        final HandledObject o = recycler.get();
        final HandledObject o2 = recycler.get();
        final HandledObject o3 = recycler.get();
        final Thread thread = new Thread() {
            @Override
            public void run() {
                o.recycle();
                o2.recycle();
                o3.recycle();
            }
        };
        thread.start();
        thread.join();
        // In reverse order
        assertSame(o3, recycler.get());
        assertSame(o, recycler.get());
    }

    /**
     * Test to make sure bug #2848 never happens again
     * https://github.com/netty/netty/issues/2848
     */
    @Test
    public void testMaxCapacity() {
        testMaxCapacity(300);
        Random rand = new Random();
        for (int i = 0; i < 50; i++) {
            testMaxCapacity(rand.nextInt(1000) + 256); // 256 - 1256
        }
    }

    private static void testMaxCapacity(int maxCapacity) {
        Recycler<HandledObject> recycler = newRecycler(maxCapacity);
        HandledObject[] objects = new HandledObject[maxCapacity * 3];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = recycler.get();
        }

        for (int i = 0; i < objects.length; i++) {
            objects[i].recycle();
            objects[i] = null;
        }

        assertTrue(maxCapacity >= recycler.threadLocalCapacity(),
                "The threadLocalCapacity (" + recycler.threadLocalCapacity() + ") must be <= maxCapacity ("
                + maxCapacity + ") as we not pool all new handles internally");
    }

    @Test
    public void testRecycleAtDifferentThread() throws Exception {
        final Recycler<HandledObject> recycler = newRecycler(256, 10, 2, 10, 2);
        final HandledObject o = recycler.get();
        final HandledObject o2 = recycler.get();

        final Thread thread = new Thread() {
            @Override
            public void run() {
                o.recycle();
                o2.recycle();
            }
        };
        thread.start();
        thread.join();

        assertSame(recycler.get(), o);
        assertNotSame(recycler.get(), o2);
    }

    @Test
    public void testRecycleAtTwoThreadsMulti() throws Exception {
        final Recycler<HandledObject> recycler = newRecycler(256);
        final HandledObject o = recycler.get();

        ExecutorService single = Executors.newSingleThreadExecutor();

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

    @Test
    public void testMaxCapacityWithRecycleAtDifferentThread() throws Exception {
        final int maxCapacity = 4; // Choose the number smaller than WeakOrderQueue.LINK_CAPACITY
        final Recycler<HandledObject> recycler = newRecycler(maxCapacity);

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

        final Thread thread = new Thread() {
            @Override
            public void run() {
                for (int i = maxCapacity; i < array.length; i ++) {
                    array[i].recycle();
                }
            }
        };
        thread.start();
        thread.join();

        assertEquals(maxCapacity, recycler.threadLocalCapacity());
        assertEquals(1, recycler.threadLocalSize());

        for (int i = 0; i < array.length; i ++) {
            recycler.get();
        }

        assertEquals(maxCapacity, recycler.threadLocalCapacity());
        assertEquals(0, recycler.threadLocalSize());
    }

    @Test
    public void testDiscardingExceedingElementsWithRecycleAtDifferentThread() throws Exception {
        final int maxCapacity = 32;
        final AtomicInteger instancesCount = new AtomicInteger(0);

        final Recycler<HandledObject> recycler = new Recycler<HandledObject>(maxCapacity, 2) {
            @Override
            protected HandledObject newObject(Recycler.Handle<HandledObject> handle) {
                instancesCount.incrementAndGet();
                return new HandledObject(handle);
            }
        };

        // Borrow 2 * maxCapacity objects.
        final HandledObject[] array = new HandledObject[maxCapacity * 2];
        for (int i = 0; i < array.length; i++) {
            array[i] = recycler.get();
        }

        assertEquals(array.length, instancesCount.get());
        // Reset counter.
        instancesCount.set(0);

        // Recycle from other thread.
        final Thread thread = new Thread() {
            @Override
            public void run() {
                for (HandledObject object: array) {
                    object.recycle();
                }
            }
        };
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
