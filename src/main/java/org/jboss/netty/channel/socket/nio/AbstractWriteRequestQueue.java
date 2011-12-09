/*
 * Copyright 2011 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
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

/**
 * 
 *
 */
abstract class AbstractWriteRequestQueue implements BlockingQueue<MessageEvent>{

    protected final BlockingQueue<MessageEvent> queue;

    public AbstractWriteRequestQueue() {
        this.queue = QueueFactory.createQueue(MessageEvent.class);
    }

    /*
     * (non-Javadoc)
     * @see java.util.Queue#remove()
     */
    public MessageEvent remove() {
        return queue.remove();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Queue#element()
     */
    public MessageEvent element() {
        return queue.element();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Queue#peek()
     */
    public MessageEvent peek() {
        return queue.peek();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#size()
     */
    public int size() {
        return queue.size();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#isEmpty()
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#iterator()
     */
    public Iterator<MessageEvent> iterator() {
        return queue.iterator();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#toArray()
     */
    public Object[] toArray() {
        return queue.toArray();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#toArray(T[])
     */
    public <T> T[] toArray(T[] a) {
        return queue.toArray(a);
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#containsAll(java.util.Collection)
     */
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#addAll(java.util.Collection)
     */
    public boolean addAll(Collection<? extends MessageEvent> c) {
        return queue.addAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#removeAll(java.util.Collection)
     */
    public boolean removeAll(Collection<?> c) {
        return queue.removeAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#retainAll(java.util.Collection)
     */
    public boolean retainAll(Collection<?> c) {
        return queue.retainAll(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.Collection#clear()
     */
    public void clear() {
        queue.clear();        
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#add(java.lang.Object)
     */
    public boolean add(MessageEvent e) {
        return queue.add(e);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#put(java.lang.Object)
     */
    public void put(MessageEvent e) throws InterruptedException {
        queue.put(e);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#offer(java.lang.Object, long, java.util.concurrent.TimeUnit)
     */
    public boolean offer(MessageEvent e, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(e, timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#take()
     */
    public MessageEvent take() throws InterruptedException {
        return queue.take();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#poll(long, java.util.concurrent.TimeUnit)
     */
    public MessageEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#remainingCapacity()
     */
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#remove(java.lang.Object)
     */
    public boolean remove(Object o) {
        return queue.remove(o);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#contains(java.lang.Object)
     */
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#drainTo(java.util.Collection)
     */
    public int drainTo(Collection<? super MessageEvent> c) {
        return queue.drainTo(c);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#drainTo(java.util.Collection, int)
     */
    public int drainTo(Collection<? super MessageEvent> c, int maxElements) {
        return queue.drainTo(c, maxElements);
    }
  
}
