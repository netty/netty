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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

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
        assertSame(object, object2);
        object2.recycle();
    }

    static final class RecyclableObject {

        private static final Recycler<RecyclableObject> RECYCLER = new Recycler<RecyclableObject>() {
            @Override
            protected RecyclableObject newObject(Handle<RecyclableObject> handle) {
                return new RecyclableObject(handle);
            }
        };

        private final Recycler.Handle<RecyclableObject> handle;

        private RecyclableObject(Recycler.Handle<RecyclableObject> handle) {
            this.handle = handle;
        }

        public static RecyclableObject newInstance() {
            return RECYCLER.get();
        }

        public void recycle() {
            RECYCLER.recycle(this, handle);
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
            protected HandledObject newObject(
                    Recycler.Handle<HandledObject> handle) {
                return new HandledObject(handle);
            }
        };

        HandledObject[] objects = new HandledObject[maxCapacity * 3];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = recycler.get();
        }

        for (int i = 0; i < objects.length; i++) {
            recycler.recycle(objects[i], objects[i].handle);
            objects[i] = null;
        }

        assertEquals(maxCapacity, recycler.threadLocalCapacity());
    }

    @Test
    public void testRecycleAtDifferentThread() throws Exception {
        final Recycler<HandledObject> recycler = new Recycler<HandledObject>(256) {
            @Override
            protected HandledObject newObject(Recycler.Handle handle) {
                return new HandledObject(handle);
            }
        };

        final HandledObject o = recycler.get();
        final Thread thread = new Thread() {
            @Override
            public void run() {
                recycler.recycle(o, o.handle);
            }
        };
        thread.start();
        thread.join();

        assertThat(recycler.get(), is(sameInstance(o)));
    }

    @Test
    public void testMaxCapacityWithRecycleAtDifferentThread() throws Exception {
        final int maxCapacity = 4; // Choose the number smaller than WeakOrderQueue.LINK_CAPACITY
        final Recycler<HandledObject> recycler = new Recycler<HandledObject>(maxCapacity) {
            @Override
            protected HandledObject newObject(Recycler.Handle handle) {
                return new HandledObject(handle);
            }
        };

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

        assertThat(recycler.threadLocalCapacity(), is(maxCapacity));
        assertThat(recycler.threadLocalSize(), is(maxCapacity));

        for (int i = 0; i < array.length; i ++) {
            recycler.get();
        }

        assertThat(recycler.threadLocalCapacity(), is(maxCapacity));
        assertThat(recycler.threadLocalSize(), is(0));
    }

    static final class HandledObject {
        Recycler.Handle<HandledObject> handle;

        HandledObject(Recycler.Handle<HandledObject> handle) {
            this.handle = handle;
        }
    }
}
