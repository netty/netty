/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.channel.MessageEvent;
import io.netty.util.internal.QueueFactory;

abstract class AbstractWriteRequestQueue implements BlockingQueue<MessageEvent> {

    protected final BlockingQueue<MessageEvent> queue;

    public AbstractWriteRequestQueue() {
        this.queue = QueueFactory.createQueue(MessageEvent.class);
    }

    @Override
    public MessageEvent remove() {
        return queue.remove();
    }

    @Override
    public MessageEvent element() {
        return queue.element();
    }

    @Override
    public MessageEvent peek() {
        return queue.peek();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Iterator<MessageEvent> iterator() {
        return queue.iterator();
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return queue.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends MessageEvent> c) {
        return queue.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return queue.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return queue.retainAll(c);
    }

    @Override
    public void clear() {
        queue.clear();        
    }

    @Override
    public boolean add(MessageEvent e) {
        return queue.add(e);
    }

    @Override
    public void put(MessageEvent e) throws InterruptedException {
        queue.put(e);
    }

    @Override
    public boolean offer(MessageEvent e, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(e, timeout, unit);
    }

    @Override
    public MessageEvent take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public MessageEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    @Override
    public boolean remove(Object o) {
        return queue.remove(o);
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public int drainTo(Collection<? super MessageEvent> c) {
        return queue.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super MessageEvent> c, int maxElements) {
        return queue.drainTo(c, maxElements);
    }
  
}
