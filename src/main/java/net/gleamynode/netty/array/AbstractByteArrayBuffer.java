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
package net.gleamynode.netty.array;

import java.util.NoSuchElementException;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public abstract class AbstractByteArrayBuffer extends AbstractByteArray implements ByteArrayBuffer {

    public ByteArray read(ByteArrayIndexFinder endIndexFinder) {
        long endIndex = indexOf(firstIndex(), endIndexFinder);
        if (endIndex == NOT_FOUND) {
            throw new NoSuchElementException();
        }
        return read((int) endIndex - firstIndex());
    }

    public void skip(ByteArrayIndexFinder firstIndexFindex) {
        long newFirstIndex = indexOf(firstIndex(), firstIndexFindex);
        if (newFirstIndex == NOT_FOUND) {
            throw new NoSuchElementException();
        }
        skip((int) newFirstIndex - firstIndex());
    }
}
