/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.channel.group;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
final class CombinedIterator<E> implements Iterator<E> {

    private final Iterator<E> i1;
    private final Iterator<E> i2;
    private Iterator<E> currentIterator;

    CombinedIterator(Iterator<E> i1, Iterator<E> i2) {
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
