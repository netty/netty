/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.buffer;

import java.util.Iterator;
import java.util.Queue;

public class QueueBackedObjectBuffer<E> implements ObjectBuffer<E> {

    private final ObjectBufferListenerSupport<E> listenerSupport =
        new ObjectBufferListenerSupport<E>(this);
    private final Queue<E> queue;

    public QueueBackedObjectBuffer(Queue<E> queue) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
    }

    public void addListener(ObjectBufferListener<? super E> listener) {
        listenerSupport.addListener(listener);
    }

    public void removeListener(ObjectBufferListener<? super E> listener) {
        listenerSupport.removeListener(listener);
    }

    public E read() {
        E item = queue.remove();
        listenerSupport.notifyRemoval(item);
        return item;
    }

    public void write(E item) {
        if (item == null) {
            throw new NullPointerException("item");
        }

        listenerSupport.assureAcceptance(item);
        queue.add(item);
        listenerSupport.notifyAddition(item);
    }


    public E elementAt(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        int i = 0;
        for (E e: queue) {
            if (i == index) {
                return e;
            }
            i ++;
        }

        throw new IndexOutOfBoundsException(String.valueOf(index));
    }

    public boolean empty() {
        return queue.isEmpty();
    }

    public int count() {
        return queue.size();
    }

    public Iterator<E> iterator() {
        final Iterator<E> it = queue.iterator();

        return new Iterator<E>() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public E next() {
                return it.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
