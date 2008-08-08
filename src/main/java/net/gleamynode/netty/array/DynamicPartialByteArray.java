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



public class DynamicPartialByteArray extends PartialByteArray {

    private int firstIndex;
    private int length;

    public DynamicPartialByteArray(ByteArray array) {
        super (array);

        firstIndex = array.firstIndex();
        length = array.length();
    }

    @Override
    public int firstIndex() {
        return firstIndex;
    }

    @Override
    public int endIndex() {
        return firstIndex + length;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    protected void setFirstIndex(int firstIndex) {
        this.firstIndex = firstIndex;
        clearHashCode();
    }

    @Override
    protected void setLength(int length) {
        this.length = length;
        clearHashCode();
    }
}
