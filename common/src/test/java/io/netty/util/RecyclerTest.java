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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class RecyclerTest {

    private static Recycler<HandledObject> newRecycler(int max) {
        return new Recycler<HandledObject>(max) {
            @Override
            protected HandledObject newObject(
                    Recycler.Handle<HandledObject> handle) {
                return new HandledObject(handle);
            }
        };
    }

    @Test(timeout = 5000L)
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

    @Test(expected = IllegalStateException.class)
    public void testMultipleRecycle() {
        Recycler<HandledObject> recycler = newRecycler(1024);
        HandledObject object = recycler.get();
        object.recycle();
        object.recycle();
    }

    @Test(expected = IllegalStateException.class)
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
        IllegalStateException exception = exceptionStore.get();
        if (exception != null) {
            throw exception;
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
    public void testRecycleAtDifferentThread() throws Exception {
        final Recycler<HandledObject> recycler = new Recycler<HandledObject>(256, 10, 2, 10) {
            @Override
            protected HandledObject newObject(Recycler.Handle<HandledObject> handle) {
                return new HandledObject(handle);
            }
        };

        class CreateObjectsThread extends Thread {
            HandledObject o;
            HandledObject o2;

            @Override
            public void run() {
                o = recycler.get(); // This will be recyclable
                o2 = recycler.get(); // This won't be, because of allocationCount
            }
        }

        final CreateObjectsThread thread = new CreateObjectsThread();
        thread.start();
        thread.join();
        thread.o.recycle();
        thread.o2.recycle();

        assertSame(thread.o, recycler.get());
        assertNotSame(thread.o2, recycler.get());
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
        assertTrue("The instances count (" +  instancesCount.get() + ") must be <= array.length (" + array.length
                + ") - maxCapacity (" + maxCapacity + ") / 2 as we not pool all new handles" +
                " internally", array.length - maxCapacity / 2 <= instancesCount.get());
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
