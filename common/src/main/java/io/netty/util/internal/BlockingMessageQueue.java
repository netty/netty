/*
 * Copyright 2022 The Netty Project
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

import org.jctools.queues.MessagePassingQueue;

import java.util.ArrayDeque;

/**
 * This is an implementation of {@link MessagePassingQueue}, similar to what might be returned from
 * {@link PlatformDependent#newMpscQueue(int)}, but intended to be used for debugging purpose.
 * The implementation relies on synchronised monitor locks for thread-safety.
 * The {@code drain} and {@code fill} bulk operations are not supported by this implementation.
 */
@UnstableApi
public final class BlockingMessageQueue<T> implements MessagePassingQueue<T> {
    private final ArrayDeque<T> deque;
    private final int maxCapacity;

    public BlockingMessageQueue(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        deque = new ArrayDeque<T>();
    }

    @Override
    public synchronized boolean offer(T e) {
        if (deque.size() == maxCapacity) {
            return false;
        }
        return deque.offer(e);
    }

    @Override
    public synchronized T poll() {
        return deque.poll();
    }

    @Override
    public synchronized T peek() {
        return deque.peek();
    }

    @Override
    public synchronized int size() {
        return deque.size();
    }

    @Override
    public synchronized void clear() {
        deque.clear();
    }

    @Override
    public synchronized boolean isEmpty() {
        return deque.isEmpty();
    }

    @Override
    public int capacity() {
        return maxCapacity;
    }

    @Override
    public boolean relaxedOffer(T e) {
        return offer(e);
    }

    @Override
    public T relaxedPoll() {
        return poll();
    }

    @Override
    public T relaxedPeek() {
        return peek();
    }

    @Override
    public int drain(Consumer<T> c, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fill(Supplier<T> s, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drain(Consumer<T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fill(Supplier<T> s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit) {
        throw new UnsupportedOperationException();
    }
}
