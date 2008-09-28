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
public class FastQueue<E> {

    static final int INITIAL_CAPACITY = 4;

    // Put
    private Object[] elements;
    private int size;

    // Take
    private Object[] drainedElements;
    private int drainedElementCount;
    private int index;

    public synchronized void offer(E e) {
        if (elements == null) {
            elements = new Object[INITIAL_CAPACITY];
        } else if (size == elements.length) {
            Object[] newElements = new Object[size << 1];
            System.arraycopy(elements, 0, newElements, 0, size);
            elements = newElements;
        }

        elements[size ++] = e;
    }

    public E poll() {
        for (;;) {
            if (drainedElements == null) {
                synchronized (this) {
                    drainedElements = elements;
                    if (elements == null) {
                        break;
                    }
                    drainedElementCount = size;
                    elements = null;
                    size = 0;
                }
                index = 0;
            }

            if (index < drainedElementCount) {
                return cast(drainedElements[index ++]);
            }

            drainedElements = null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private E cast(Object o) {
        return (E) o;
    }
}
