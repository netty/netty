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
package org.jboss.netty.channel.socket.nio;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.internal.QueueFactory;

abstract class AbstractWriteRequestQueue implements BlockingQueue<MessageEvent> {

    protected final BlockingQueue<MessageEvent> queue;

    public AbstractWriteRequestQueue() {
        this.queue = QueueFactory.createQueue(MessageEvent.class);
    }

    public MessageEvent remove() {
        return queue.remove();
    }

    public MessageEvent element() {
        return queue.element();
    }

    public MessageEvent peek() {
        return queue.peek();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public Iterator<MessageEvent> iterator() {
        return queue.iterator();
    }

    public Object[] toArray() {
        return queue.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return queue.toArray(a);
    }

    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    public boolean addAll(Collection<? extends MessageEvent> c) {
        return queue.addAll(c);
    }

    public boolean removeAll(Collection<?> c) {
        return queue.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return queue.retainAll(c);
    }

    public void clear() {
        queue.clear();        
    }

    public boolean add(MessageEvent e) {
        return queue.add(e);
    }

    public void put(MessageEvent e) throws InterruptedException {
        queue.put(e);
    }

    public boolean offer(MessageEvent e, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(e, timeout, unit);
    }

    public MessageEvent take() throws InterruptedException {
        return queue.take();
    }

    public MessageEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public boolean remove(Object o) {
        return queue.remove(o);
    }

    public boolean contains(Object o) {
        return queue.contains(o);
    }

    public int drainTo(Collection<? super MessageEvent> c) {
        return queue.drainTo(c);
    }

    public int drainTo(Collection<? super MessageEvent> c, int maxElements) {
        return queue.drainTo(c, maxElements);
    }
}
