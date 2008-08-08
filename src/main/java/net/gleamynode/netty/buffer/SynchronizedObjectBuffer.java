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

public class SynchronizedObjectBuffer<E> implements ObjectBuffer<E> {

    protected final ObjectBuffer<E> buffer;

    public SynchronizedObjectBuffer(ObjectBuffer<E> buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
    }

    public void addListener(ObjectBufferListener<? super E> listener) {
        buffer.addListener(listener);
    }

    public void removeListener(ObjectBufferListener<? super E> listener) {
        buffer.removeListener(listener);
    }

    public synchronized int count() {
        return buffer.count();
    }

    public synchronized E elementAt(int index) {
        return buffer.elementAt(index);
    }

    public synchronized boolean empty() {
        return buffer.empty();
    }

    public synchronized Iterator<E> iterator() {
        return buffer.iterator();
    }

    public synchronized E read() {
        return buffer.read();
    }

    public synchronized void write(E element) {
        buffer.write(element);
    }
}
