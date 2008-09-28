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

import java.util.LinkedList;
import java.util.Queue;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class FastQueue<E> {

    // Put
    private Queue<E> offeredElements;

    // Take
    private Queue<E> drainedElements;

    public synchronized void offer(E e) {
        if (offeredElements == null) {
            offeredElements = new LinkedList<E>();
        }

        offeredElements.offer(e);
    }

    public E poll() {
        for (;;) {
            if (drainedElements == null) {
                synchronized (this) {
                    drainedElements = offeredElements;
                    if (offeredElements == null) {
                        break;
                    }
                    offeredElements = null;
                }
            }

            E e = cast(drainedElements.poll());
            if (e != null) {
                return e;
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
