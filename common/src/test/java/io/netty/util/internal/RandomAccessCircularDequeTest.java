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
package io.netty.util.internal;

import org.junit.Test;

import static org.junit.Assert.*;

public class RandomAccessCircularDequeTest {

    @Test
    public void testSimpleOfferSetPoll() {
        RandomAccessCircularDeque<Integer> queue = new RandomAccessCircularDeque<Integer>(4);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        queue.offer(0);
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());

        queue.set(0, 1);
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());

        assertEquals(1, (int) queue.poll());

        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testOfferPollWithoutGrow() {
        RandomAccessCircularDeque<Integer> queue = new RandomAccessCircularDeque<Integer>(4);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        // Ensure we not grow it yet.
        for (int i = 0; i < queue.capacity() - 1; i++) {
            queue.offer(i);
        }
        assertEquals(3, queue.capacity() - 1);
        assertFalse(queue.isEmpty());

        for (int i = 0; i < queue.capacity() - 1; i++) {
            assertEquals(i, (int) queue.poll());
        }

        // Check if we flatten it after all was removed.
        assertEquals(0, queue.head());
        assertEquals(0, queue.tail());
    }

    @Test
    public void testOfferPollWithGrow() {
        RandomAccessCircularDeque<Integer> queue = new RandomAccessCircularDeque<Integer>(4);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        int elements = queue.capacity() * 2;
        // Ensure we not grow it yet.
        for (int i = 0; i < elements; i++) {
            queue.offer(i);
        }
        assertEquals(elements, queue.size());
        assertFalse(queue.isEmpty());

        for (int i = 0; i < elements; i++) {
            assertEquals(i, (int) queue.poll());
        }

        // Check if we flatten it after all was removed.
        assertEquals(0, queue.head());
        assertEquals(0, queue.tail());
    }

    @Test
    public void testAddWithIndexWithFlatArray() {
        RandomAccessCircularDeque<Integer> queue = new RandomAccessCircularDeque<Integer>(8);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        queue.offer(0);
        queue.offer(1);
        queue.offer(2);

        assertTrue(queue.storageIsFlat());

        assertEquals(0, (int) queue.get(0));
        assertEquals(1, (int) queue.get(1));
        assertEquals(2, (int) queue.get(2));

        queue.add(1, 8);

        assertEquals(0, (int) queue.get(0));
        assertEquals(8, (int) queue.get(1));
        assertEquals(1, (int) queue.get(2));
        assertEquals(2, (int) queue.get(3));
        assertTrue(queue.storageIsFlat());
    }

    @Test
    public void testAddWithIndexWithOutFlatArray() {
        RandomAccessCircularDeque<Integer> queue = new RandomAccessCircularDeque<Integer>(8);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        for (int i = 0; i < queue.capacity() - 1; i++) {
            queue.offer(0);
        }

        assertTrue(queue.storageIsFlat());

        queue.remove();
        queue.offer(0);
        queue.remove();
        queue.offer(0);
        queue.remove();

        assertFalse(queue.storageIsFlat());

        assertEquals(8, queue.capacity());
        assertEquals(6, queue.size());

        queue.add(5, 1);
        assertEquals(7, queue.size());

        assertTrue(queue.storageIsFlat());

        assertEquals(0, (int) queue.get(0));
        assertEquals(0, (int) queue.get(1));
        assertEquals(0, (int) queue.get(2));
        assertEquals(0, (int) queue.get(2));
        assertEquals(0, (int) queue.get(3));
        assertEquals(0, (int) queue.get(4));
        assertEquals(1, (int) queue.get(5));
        assertEquals(0, (int) queue.get(6));
    }

    @Test
    public void testRemoveRange() {
        RandomAccessCircularDeque<Integer> queue = new RandomAccessCircularDeque<Integer>(8);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        int value = 0;

        for (int i = 0; i < queue.capacity() - 1; i++) {
            queue.offer(value++);
        }

        assertEquals(7, queue.size());
        assertTrue(queue.storageIsFlat());

        assertEquals(0, (int) queue.get(0));
        assertEquals(1, (int) queue.get(1));
        assertEquals(2, (int) queue.get(2));
        assertEquals(3, (int) queue.get(3));
        assertEquals(4, (int) queue.get(4));
        assertEquals(5, (int) queue.get(5));
        assertEquals(6, (int) queue.get(6));

        queue.remove(2, 2);
        assertEquals(5, queue.size());

        assertEquals(0, (int) queue.get(0));
        assertEquals(1, (int) queue.get(1));
        assertEquals(4, (int) queue.get(2));
        assertEquals(5, (int) queue.get(3));
        assertEquals(6, (int) queue.get(4));

        queue.add(value);

        assertEquals(0, (int) queue.get(0));
        assertEquals(1, (int) queue.get(1));
        assertEquals(4, (int) queue.get(2));
        assertEquals(5, (int) queue.get(3));
        assertEquals(6, (int) queue.get(4));
        assertEquals(7, (int) queue.get(5));
    }

    @Test
    public void testRemoveRangeWithoutFlatArray() {
        RandomAccessCircularDeque<Integer> queue = new RandomAccessCircularDeque<Integer>(8);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());

        int value = 0;
        for (int i = 0; i < queue.capacity() - 1; i++) {
            queue.offer(value++);
        }

        assertTrue(queue.storageIsFlat());

        assertEquals(0, (int) queue.get(0));
        assertEquals(1, (int) queue.get(1));
        assertEquals(2, (int) queue.get(2));
        assertEquals(3, (int) queue.get(3));
        assertEquals(4, (int) queue.get(4));
        assertEquals(5, (int) queue.get(5));
        assertEquals(6, (int) queue.get(6));

        queue.remove();
        assertEquals(1, (int) queue.get(0));

        queue.add(value++);
        queue.remove();
        assertEquals(2, (int) queue.get(0));

        queue.add(value++);
        queue.remove();
        assertEquals(3, (int) queue.get(0));

        assertFalse(queue.storageIsFlat());

        assertEquals(6, queue.size());
        assertEquals(3, (int) queue.get(0));
        assertEquals(4, (int) queue.get(1));
        assertEquals(5, (int) queue.get(2));
        assertEquals(6, (int) queue.get(3));
        assertEquals(7, (int) queue.get(4));
        assertEquals(8, (int) queue.get(5));

        queue.remove(2, 2);
        assertEquals(4, queue.size());
        assertEquals(3, (int) queue.get(0));
        assertEquals(4, (int) queue.get(1));
        assertEquals(7, (int) queue.get(2));
        assertEquals(8, (int) queue.get(3));

        queue.add(value);

        assertEquals(3, (int) queue.get(0));
        assertEquals(4, (int) queue.get(1));
        assertEquals(7, (int) queue.get(2));
        assertEquals(8, (int) queue.get(3));
        assertEquals(9, (int) queue.get(4));
    }
}
