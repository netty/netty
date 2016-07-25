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

import org.junit.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RecyclerTest {

    private static Recycler<HandledObject> newRecycler(int max) {
        return new Recycler<HandledObject>(max) {
            @Override
            protected HandledObject newObject(
                    Recycler.Handle handle) {
                return new HandledObject(handle);
            }
        };
    }

    @Test(expected = IllegalStateException.class)
    public void testMultipleRecycle() {
        Recycler<HandledObject> recycler = newRecycler(1024);
        HandledObject object = recycler.get();
        recycler.recycle(object, object.handle);
        recycler.recycle(object, object.handle);
    }

    @Test
    public void testRecycle() {
        Recycler<HandledObject> recycler = newRecycler(1024);
        HandledObject object = recycler.get();
        recycler.recycle(object, object.handle);
        HandledObject object2 = recycler.get();
        assertSame(object, object2);
        recycler.recycle(object2, object2.handle);
    }

    @Test
    public void testRecycleDisable() {
        Recycler<HandledObject> recycler = newRecycler(-1);
        HandledObject object = recycler.get();
        recycler.recycle(object, object.handle);
        HandledObject object2 = recycler.get();
        assertNotSame(object, object2);
        recycler.recycle(object2, object2.handle);
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
            recycler.recycle(objects[i], objects[i].handle);
            objects[i] = null;
        }

        assertTrue("The threadLocalCapacity (" + recycler.threadLocalCapacity() + ") must be <= maxCapacity ("
                + maxCapacity + ") as we not pool all new handles internally",
                maxCapacity >= recycler.threadLocalCapacity());
    }

    @Test
    public void testRecycleAtDifferentThread() throws Exception {
        final Recycler<HandledObject> recycler = new Recycler<HandledObject>(256, 10, 2, 10) {
            @Override
            protected HandledObject newObject(Recycler.Handle handle) {
                return new HandledObject(handle);
            }
        };

        final HandledObject o = recycler.get();
        final HandledObject o2 = recycler.get();
        final Thread thread = new Thread() {
            @Override
            public void run() {
                recycler.recycle(o, o.handle);
                recycler.recycle(o2, o2.handle);
            }
        };
        thread.start();
        thread.join();

        assertSame(recycler.get(), o);
        assertNotSame(recycler.get(), o2);
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
            recycler.recycle(array[i], array[i].handle);
        }

        final Thread thread = new Thread() {
            @Override
            public void run() {
                for (int i = maxCapacity; i < array.length; i ++) {
                    recycler.recycle(array[i], array[i].handle);
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
            protected HandledObject newObject(Recycler.Handle handle) {
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
                    recycler.recycle(object, object.handle);
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
        assertTrue("The instances count (" +  instancesCount.get() + ") must be <= array.length (" + array.length
                + ") - maxCapacity (" + maxCapacity + ") / 2 as we not pool all new handles" +
                " internally", array.length - maxCapacity / 2 <= instancesCount.get());
    }

    static final class HandledObject {
        Recycler.Handle handle;

        HandledObject(Recycler.Handle handle) {
            this.handle = handle;
        }
    }
}
