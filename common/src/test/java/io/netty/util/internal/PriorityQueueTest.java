/*
 * Copyright 2015 The Netty Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PriorityQueueTest {
    @Test
    public void testPoll() {
        PriorityQueue<TestElement> queue = new PriorityQueue<TestElement>(0);
        assertEmptyQueue(queue);

        TestElement a = new TestElement(5);
        TestElement b = new TestElement(10);
        TestElement c = new TestElement(2);
        TestElement d = new TestElement(7);
        TestElement e = new TestElement(6);

        assertOffer(queue, a);
        assertOffer(queue, b);
        assertOffer(queue, c);
        assertOffer(queue, d);

        // Remove the first element
        assertSame(c, queue.peek());
        assertSame(c, queue.poll());
        assertEquals(3, queue.size());

        // Test that offering another element preserves the priority queue semantics.
        assertOffer(queue, e);
        assertEquals(4, queue.size());
        assertSame(a, queue.peek());
        assertSame(a, queue.poll());
        assertEquals(3, queue.size());

        // Keep removing the remaining elements
        assertSame(e, queue.peek());
        assertSame(e, queue.poll());
        assertEquals(2, queue.size());

        assertSame(d, queue.peek());
        assertSame(d, queue.poll());
        assertEquals(1, queue.size());

        assertSame(b, queue.peek());
        assertSame(b, queue.poll());
        assertEmptyQueue(queue);
    }

    @Test
    public void testClear() {
        PriorityQueue<TestElement> queue = new PriorityQueue<TestElement>(0);
        assertEmptyQueue(queue);

        TestElement a = new TestElement(5);
        TestElement b = new TestElement(10);
        TestElement c = new TestElement(2);
        TestElement d = new TestElement(6);

        assertOffer(queue, a);
        assertOffer(queue, b);
        assertOffer(queue, c);
        assertOffer(queue, d);

        queue.clear();
        assertEmptyQueue(queue);

        // Test that elements can be re-inserted after the clear operation
        assertOffer(queue, a);
        assertSame(a, queue.peek());

        assertOffer(queue, b);
        assertSame(a, queue.peek());

        assertOffer(queue, c);
        assertSame(c, queue.peek());

        assertOffer(queue, d);
        assertSame(c, queue.peek());
    }

    @Test
    public void testRemoval() {
        PriorityQueue<TestElement> queue = new PriorityQueue<TestElement>(4);
        assertEmptyQueue(queue);

        TestElement a = new TestElement(5);
        TestElement b = new TestElement(10);
        TestElement c = new TestElement(2);
        TestElement d = new TestElement(6);
        TestElement notInQueue = new TestElement(-1);

        assertOffer(queue, a);
        assertOffer(queue, b);
        assertOffer(queue, c);
        assertOffer(queue, d);

        // Remove an element that isn't in the queue.
        assertFalse(queue.remove(notInQueue));
        assertSame(c, queue.peek());
        assertEquals(4, queue.size());

        // Remove the last element in the array, when the array is non-empty.
        assertTrue(queue.remove(b));
        assertSame(c, queue.peek());
        assertEquals(3, queue.size());

        // Re-insert the element after removal
        assertOffer(queue, b);
        assertSame(c, queue.peek());
        assertEquals(4, queue.size());

        // Repeat remove the last element in the array, when the array is non-empty.
        assertTrue(queue.remove(b));
        assertSame(c, queue.peek());
        assertEquals(3, queue.size());

        // Remove the head of the queue.
        assertTrue(queue.remove(c));
        assertSame(a, queue.peek());
        assertEquals(2, queue.size());

        assertTrue(queue.remove(a));
        assertSame(d, queue.peek());
        assertEquals(1, queue.size());

        assertTrue(queue.remove(d));
        assertEmptyQueue(queue);
    }

    @Test
    public void testZeroInitialSize() {
        PriorityQueue<TestElement> queue = new PriorityQueue<TestElement>(0);
        assertEmptyQueue(queue);
        TestElement e = new TestElement(1);
        assertOffer(queue, e);
        assertSame(e, queue.peek());
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
        assertSame(e, queue.poll());
        assertEmptyQueue(queue);
    }

    private static void assertOffer(PriorityQueue<TestElement> queue, TestElement a) {
        assertTrue(queue.offer(a));
        assertTrue(queue.contains(a));
        try { // An element can not be inserted more than 1 time.
            queue.offer(a);
            fail();
        } catch (IllegalArgumentException ignored) {
            // ignored
        }
    }

    private static void assertEmptyQueue(PriorityQueue<TestElement> queue) {
        assertNull(queue.peek());
        assertNull(queue.poll());
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    private static final class TestElement implements Comparable<TestElement>, PriorityQueueNode<TestElement> {
        int value;
        private int priorityQueueIndex = INDEX_NOT_IN_QUEUE;

        public TestElement(int value) {
            this.value = value;
        }

        @Override
        public int compareTo(TestElement o) {
            return value - o.value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestElement)) {
                return false;
            }
            return ((TestElement) o).value == value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public int priorityQueueIndex() {
            return priorityQueueIndex;
        }

        @Override
        public void priorityQueueIndex(int i) {
            priorityQueueIndex = i;
        }
    }
}
