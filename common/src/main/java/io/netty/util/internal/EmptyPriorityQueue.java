/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class EmptyPriorityQueue<T> implements PriorityQueue<T> {
    private static final PriorityQueue<Object> INSTANCE = new EmptyPriorityQueue<Object>();

    private EmptyPriorityQueue() {
    }

    /**
     * Returns an unmodifiable empty {@link PriorityQueue}.
     */
    @SuppressWarnings("unchecked")
    public static <V> EmptyPriorityQueue<V> instance() {
        return (EmptyPriorityQueue<V>) INSTANCE;
    }

    @Override
    public boolean removeTyped(T node) {
        return false;
    }

    @Override
    public boolean containsTyped(T node) {
        return false;
    }

    @Override
    public void priorityChanged(T node) {
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.<T>emptyList().iterator();
    }

    @Override
    public Object[] toArray() {
        return EmptyArrays.EMPTY_OBJECTS;
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        if (a.length > 0) {
            a[0] = null;
        }
        return a;
    }

    @Override
    public boolean add(T t) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {
    }

    @Override
    public void clearIgnoringIndexes() {
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PriorityQueue && ((PriorityQueue) o).isEmpty();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean offer(T t) {
        return false;
    }

    @Override
    public T remove() {
        throw new NoSuchElementException();
    }

    @Override
    public T poll() {
        return null;
    }

    @Override
    public T element() {
        throw new NoSuchElementException();
    }

    @Override
    public T peek() {
        return null;
    }

    @Override
    public String toString() {
        return EmptyPriorityQueue.class.getSimpleName();
    }
}
