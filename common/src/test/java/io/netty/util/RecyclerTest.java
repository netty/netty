/*
* Copyright 2014 The Netty Project
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

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class RecyclerTest {

    @Test(expected = IllegalStateException.class)
    public void testMultipleRecycle() {
        RecyclableObject object = RecyclableObject.newInstance();
        object.recycle();
        object.recycle();
    }

    @Test
    public void testRecycle() {
        RecyclableObject object = RecyclableObject.newInstance();
        object.recycle();
        RecyclableObject object2 = RecyclableObject.newInstance();
        Assert.assertSame(object, object2);
        object2.recycle();
    }

    static final class RecyclableObject {

        private static final Recycler<RecyclableObject> RECYCLER = new Recycler<RecyclableObject>() {
            @Override
            protected RecyclableObject newObject(Handle handle) {
                return new RecyclableObject(handle);
            }
        };

        private final Recycler.Handle handle;

        private RecyclableObject(Recycler.Handle handle) {
            this.handle = handle;
        }

        public static RecyclableObject newInstance() {
            return RECYCLER.get();
        }

        public void recycle() {
            handle.recycle();
        }
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

    void testMaxCapacity(int maxCapacity) {
        Recycler<HandledObject> recycler = new Recycler<HandledObject>(maxCapacity) {
            @Override
            protected HandledObject newObject(Recycler.Handle handle) {
                return new HandledObject(handle);
            }
        };

        HandledObject[] objects = new HandledObject[maxCapacity * 3];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = recycler.get();
        }

        for (int i = 0; i < objects.length; i++) {
            objects[i].handle.recycle();
            objects[i] = null;
        }

        Assert.assertEquals(maxCapacity, recycler.threadLocalCapacity());
    }

    static final class HandledObject {
        Recycler.Handle handle;

        HandledObject(Recycler.Handle handle) {
            this.handle = handle;
        }
    }

    /**
     * Test to make sure an recycled item can not be recycled again.
     * We spawn a number a threads to multi recycle an item.
     * We count the number of exceptions thrown and test if it is the right one.
     */
    @Test
    public void testAlreadyRecycled() {
        testAlreadyRecycled(true);
        testAlreadyRecycled(false);
    }

    private static void testAlreadyRecycled(boolean recycleOwnerThreadTwice) {
        final TestData testData = new TestData();

        testData.container.clear();
        for (int i = 0; i < TestData.NUM_ITEMS; i++) {
            testData.container.add(RecyclableObject.newInstance());
        }

        ArrayList<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < TestData.NUM_THREADS; i++) {
            Thread th = new Thread() {
                @Override
                public void run() {
                    recycle(testData, false, TestData.NUM_THREADS);
                }
            };
            th.start();
            threads.add(th);
        }

        if (recycleOwnerThreadTwice) {
            testData.counterRecyclers.incrementAndGet();
            for (RecyclableObject testObject : testData.container) {
                testObject.recycle();
            }
        }

        recycle(testData, true, TestData.NUM_THREADS);

        for (Thread th : threads) {
            try {
                th.join();
            } catch (Exception ignored) {
                continue;
            }
        }

        int expected = (testData.counterRecyclers.get() - 1) * TestData.NUM_ITEMS;
        int got = testData.counterExceptions.get();
        Assert.assertEquals(expected, got);
    }

    private static void recycle(final TestData testData, final boolean master, final int extraRecyclers) {
        try {
            testData.counterRecyclers.incrementAndGet();

            for (RecyclableObject testObject : testData.container) {
                try {
                    if (master) {
                        testData.firstSemaphore.release(extraRecyclers);
                    } else {
                        testData.firstSemaphore.acquire();
                    }
                    try {
                        testObject.recycle();
                    } catch (Exception ignored) {
                        testData.counterExceptions.incrementAndGet();
                    }
                } finally {
                    if (master) {
                        testData.secondSemaphore.acquire(extraRecyclers);
                    } else {
                        testData.secondSemaphore.release();
                    }
                }
            }
        } catch (InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static final class TestData {
        private static final int NUM_THREADS = 10;
        private static final int NUM_ITEMS = 10000;

        public ArrayList<RecyclableObject> container = new ArrayList<RecyclableObject>();
        public AtomicInteger counterExceptions = new AtomicInteger(0);
        public AtomicInteger counterRecyclers = new AtomicInteger(0);
        public Semaphore firstSemaphore = new Semaphore(0);
        public Semaphore secondSemaphore = new Semaphore(0);
    }
}
