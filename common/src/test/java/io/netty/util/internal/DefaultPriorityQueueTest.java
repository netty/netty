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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultPriorityQueueTest {
    @Test
    public void testPoll() {
        PriorityQueue<TestElement> queue = new DefaultPriorityQueue<TestElement>(TestElementComparator.INSTANCE, 0);
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
        PriorityQueue<TestElement> queue = new DefaultPriorityQueue<TestElement>(TestElementComparator.INSTANCE, 0);
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
    public void testClearIgnoringIndexes() {
        PriorityQueue<TestElement> queue = new DefaultPriorityQueue<TestElement>(TestElementComparator.INSTANCE, 0);
        assertEmptyQueue(queue);

        TestElement a = new TestElement(5);
        TestElement b = new TestElement(10);
        TestElement c = new TestElement(2);
        TestElement d = new TestElement(6);
        TestElement e = new TestElement(11);

        assertOffer(queue, a);
        assertOffer(queue, b);
        assertOffer(queue, c);
        assertOffer(queue, d);

        queue.clearIgnoringIndexes();
        assertEmptyQueue(queue);

        // Elements cannot be re-inserted but new ones can.
        try {
            queue.offer(a);
            fail();
        } catch (IllegalArgumentException t) {
            // expected
        }

        assertOffer(queue, e);
        assertSame(e, queue.peek());
    }

    @Test
    public void testRemoval() {
        testRemoval(false);
    }

    @Test
    public void testRemovalTyped() {
        testRemoval(true);
    }

    private static void testRemoval(boolean typed) {
        PriorityQueue<TestElement> queue = new DefaultPriorityQueue<TestElement>(TestElementComparator.INSTANCE, 4);
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
        assertFalse(typed ? queue.removeTyped(notInQueue) : queue.remove(notInQueue));
        assertSame(c, queue.peek());
        assertEquals(4, queue.size());

        // Remove the last element in the array, when the array is non-empty.
        assertTrue(typed ? queue.removeTyped(b) : queue.remove(b));
        assertSame(c, queue.peek());
        assertEquals(3, queue.size());

        // Re-insert the element after removal
        assertOffer(queue, b);
        assertSame(c, queue.peek());
        assertEquals(4, queue.size());

        // Repeat remove the last element in the array, when the array is non-empty.
        assertTrue(typed ? queue.removeTyped(b) : queue.remove(b));
        assertSame(c, queue.peek());
        assertEquals(3, queue.size());

        // Remove the head of the queue.
        assertTrue(typed ? queue.removeTyped(c) : queue.remove(c));
        assertSame(a, queue.peek());
        assertEquals(2, queue.size());

        assertTrue(typed ? queue.removeTyped(a) : queue.remove(a));
        assertSame(d, queue.peek());
        assertEquals(1, queue.size());

        assertTrue(typed ? queue.removeTyped(d) : queue.remove(d));
        assertEmptyQueue(queue);
    }

    @Test
    public void testZeroInitialSize() {
        PriorityQueue<TestElement> queue = new DefaultPriorityQueue<TestElement>(TestElementComparator.INSTANCE, 0);
        assertEmptyQueue(queue);
        TestElement e = new TestElement(1);
        assertOffer(queue, e);
        assertSame(e, queue.peek());
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
        assertSame(e, queue.poll());
        assertEmptyQueue(queue);
    }

    @Test
    public void testPriorityChange() {
        PriorityQueue<TestElement> queue = new DefaultPriorityQueue<TestElement>(TestElementComparator.INSTANCE, 0);
        assertEmptyQueue(queue);
        TestElement a = new TestElement(10);
        TestElement b = new TestElement(20);
        TestElement c = new TestElement(30);
        TestElement d = new TestElement(25);
        TestElement e = new TestElement(23);
        TestElement f = new TestElement(15);
        queue.add(a);
        queue.add(b);
        queue.add(c);
        queue.add(d);
        queue.add(e);
        queue.add(f);

        e.value = 35;
        queue.priorityChanged(e);

        a.value = 40;
        queue.priorityChanged(a);

        a.value = 31;
        queue.priorityChanged(a);

        d.value = 10;
        queue.priorityChanged(d);

        f.value = 5;
        queue.priorityChanged(f);

        List<TestElement> expectedOrderList = new ArrayList<TestElement>(queue.size());
        expectedOrderList.addAll(Arrays.asList(a, b, c, d, e, f));
        Collections.sort(expectedOrderList, TestElementComparator.INSTANCE);

        assertEquals(expectedOrderList.size(), queue.size());
        assertEquals(expectedOrderList.isEmpty(), queue.isEmpty());
        Iterator<TestElement> itr = expectedOrderList.iterator();
        while (itr.hasNext()) {
            TestElement next = itr.next();
            TestElement poll = queue.poll();
            assertEquals(next, poll);
            itr.remove();
            assertEquals(expectedOrderList.size(), queue.size());
            assertEquals(expectedOrderList.isEmpty(), queue.isEmpty());
        }
    }

    private static void assertOffer(PriorityQueue<TestElement> queue, TestElement a) {
        assertTrue(queue.offer(a));
        assertTrue(queue.contains(a));
        assertTrue(queue.containsTyped(a));
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

    private static final class TestElementComparator implements Comparator<TestElement>, Serializable {
        private static final long serialVersionUID = 7930368853384760103L;

        static final TestElementComparator INSTANCE = new TestElementComparator();

        private TestElementComparator() {
        }

        @Override
        public int compare(TestElement o1, TestElement o2) {
            return o1.value - o2.value;
        }
    }

    private static final class TestElement implements PriorityQueueNode {
        int value;
        private int priorityQueueIndex = INDEX_NOT_IN_QUEUE;

        TestElement(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestElement && ((TestElement) o).value == value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public int priorityQueueIndex(DefaultPriorityQueue queue) {
            return priorityQueueIndex;
        }

        @Override
        public void priorityQueueIndex(DefaultPriorityQueue queue, int i) {
            priorityQueueIndex = i;
        }
    }
}
