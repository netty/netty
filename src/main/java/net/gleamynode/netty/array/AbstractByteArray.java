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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;

public abstract class AbstractByteArray implements ByteArray {

    public boolean empty() {
        return length() == 0;
    }

    public int endIndex() {
        return firstIndex() + length();
    }

    public void get(int index, byte[] dst) {
        get(index, dst, 0, dst.length);
    }

    public void get(int index, ByteArray dst) {
        get(index, dst, dst.firstIndex(), dst.length());
    }

    public void set(int index, byte[] src) {
        set(index, src, 0, src.length);
    }

    public void set(int index, ByteArray src) {
        set(index, src, src.firstIndex(), src.length());
    }

    public void copyTo(OutputStream out) throws IOException {
        copyTo(out, firstIndex(), length());
    }

    public int copyTo(WritableByteChannel out) throws IOException {
        return copyTo(out, firstIndex(), length());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' +
               "firstOffset=" + firstIndex() + ", " +
               "length=" + length() +
               ')';
    }
}
