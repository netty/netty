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
package org.jboss.netty.util.internal;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class CombinedIterator<E> implements Iterator<E> {

    private final Iterator<E> i1;
    private final Iterator<E> i2;
    private Iterator<E> currentIterator;

    public CombinedIterator(Iterator<E> i1, Iterator<E> i2) {
        if (i1 == null) {
            throw new NullPointerException("i1");
        }
        if (i2 == null) {
            throw new NullPointerException("i2");
        }
        this.i1 = i1;
        this.i2 = i2;
        currentIterator = i1;
    }

    public boolean hasNext() {
        boolean hasNext = currentIterator.hasNext();
        if (hasNext) {
            return true;
        }

        if (currentIterator == i1) {
            currentIterator = i2;
            return hasNext();
        } else {
            return false;
        }
    }

    public E next() {
        try {
            E e = currentIterator.next();
            return e;
        } catch (NoSuchElementException e) {
            if (currentIterator == i1) {
                currentIterator = i2;
                return next();
            } else {
                throw e;
            }
        }
    }

    public void remove() {
        currentIterator.remove();
    }

}
