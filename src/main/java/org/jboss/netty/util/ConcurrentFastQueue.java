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
package org.jboss.netty.util;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ConcurrentFastQueue<E> {

    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    final FastQueue<E>[] segments;
    private final int mask;
    private int pollIndex;

    public ConcurrentFastQueue() {
        this(DEFAULT_CONCURRENCY_LEVEL);
    }

    @SuppressWarnings("unchecked")
    public ConcurrentFastQueue(int concurrencyLevel) {
        if (concurrencyLevel <= 0) {
            throw new IllegalArgumentException(
                    "concurrencyLevel: " + concurrencyLevel);
        }

        int actualConcurrencyLevel = 1;
        while (actualConcurrencyLevel < concurrencyLevel) {
            actualConcurrencyLevel <<= 1;
        }

        mask = actualConcurrencyLevel - 1;
        segments = new FastQueue[actualConcurrencyLevel];
        for (int i = 0; i < actualConcurrencyLevel; i ++) {
            segments[i] = new FastQueue<E>();
        }
    }

    public void offer(E e) {
        segments[hash(e)].offer(e);
    }

    public E poll() {
        while (pollIndex < segments.length) {
            E v = segments[pollIndex].poll();
            if (v != null) {
                return v;
            }

            pollIndex ++;
        }
        pollIndex = 0;
        return null;
    }

    private int hash(Object o) {
        int hash = System.identityHashCode(o);
        hash = (hash << 1) - (hash << 8);
        hash &= mask;
        return hash;
    }
}
