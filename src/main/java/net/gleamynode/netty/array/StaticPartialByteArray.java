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


/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class StaticPartialByteArray extends PartialByteArray {

    private final int firstIndex;
    private final int endIndex;
    private final int length;

    public StaticPartialByteArray(byte[] array, int firstIndex, int length) {
        this(new HeapByteArray(array), firstIndex, length);
    }

    public StaticPartialByteArray(ByteArray array, int firstIndex, int length) {
        super (array);

        if (firstIndex < array.firstIndex()) {
            throw new IllegalArgumentException(
                    "firstIndex must be equal to or greater than " +
                    array.firstIndex() + ".");
        }
        if (length > array.length() - (firstIndex - array.firstIndex())) {
            throw new IllegalArgumentException(
                    "length must be equal to or less than " +
                    (array.length() - (firstIndex - array.firstIndex())) + ".");
        }

        this.firstIndex = firstIndex;
        this.length = length;
        endIndex = firstIndex + length;
    }

    @Override
    public int firstIndex() {
        return firstIndex;
    }

    @Override
    public int endIndex() {
        return endIndex;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public void firstIndex(int firstIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void length(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void setFirstIndex(int firstIndex) {
        throw new IllegalStateException("Should not reach here.");
    }

    @Override
    protected void setLength(int length) {
        throw new IllegalStateException("Should not reach here.");
    }
}
