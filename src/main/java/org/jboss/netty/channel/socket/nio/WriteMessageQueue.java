/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;

import org.jboss.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class WriteMessageQueue {

    private static final Queue<MessageEvent> EMPTY_QUEUE = new EmptyQueue();

    private MessageEvent[] elements;
    private int size;

    WriteMessageQueue() {
        super();
    }

    public synchronized void offer(MessageEvent e) {
        if (elements == null) {
            elements = new MessageEvent[4];
        } else if (size == elements.length) {
            MessageEvent[] newElements = new MessageEvent[size << 1];
            System.arraycopy(elements, 0, newElements, 0, size);
            elements = newElements;
        }

        elements[size ++] = e;
    }

    public synchronized Queue<MessageEvent> drainAll() {
        if (size == 0) {
            return EMPTY_QUEUE;
        }

        Queue<MessageEvent> drainedQueue = new DrainedQueue(elements, size);
        elements = null;
        size = 0;
        return drainedQueue;
    }

    private static class DrainedQueue extends AbstractQueue<MessageEvent> {

        private final MessageEvent[] elements;
        private final int nElements;
        private int index;

        DrainedQueue(MessageEvent[] elements, int nElements) {
            this.elements = elements;
            this.nElements = nElements;
        }

        @Override
        public Iterator<MessageEvent> iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return nElements - index;
        }

        public boolean offer(MessageEvent e) {
            return false;
        }

        public MessageEvent peek() {
            if (index < nElements) {
                return elements[index];
            }
            return null;
        }

        public MessageEvent poll() {
            if (index < nElements) {
                return elements[index ++];
            }
            return null;
        }
    }

    private static class EmptyQueue extends AbstractQueue<MessageEvent> {

        EmptyQueue() {
            super();
        }

        @Override
        public Iterator<MessageEvent> iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return 0;
        }

        public boolean offer(MessageEvent e) {
            return false;
        }

        public MessageEvent peek() {
            return null;
        }

        public MessageEvent poll() {
            return null;
        }
    }
}
