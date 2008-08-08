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

import java.nio.ByteBuffer;


public class ReadOnlyByteArray extends ByteArrayWrapper {

    public ReadOnlyByteArray(ByteArray array) {
        super(array);
    }

    @Override
    public void set8(int index, byte value) {
        rejectModification();
    }

    @Override
    public void set(int index, ByteArray src, int srcIndex, int length) {
        rejectModification();
    }

    @Override
    public void set(int index, byte[] src, int srcIndex, int length) {
        rejectModification();
    }

    @Override
    public void set(int index, ByteBuffer src) {
        rejectModification();
    }

    @Override
    public void setBE16(int index, short value) {
        rejectModification();
    }

    @Override
    public void setBE24(int index, int value) {
        rejectModification();
    }

    @Override
    public void setBE32(int index, int value) {
        rejectModification();
    }

    @Override
    public void setBE48(int index, long value) {
        rejectModification();
    }

    @Override
    public void setBE64(int index, long value) {
        rejectModification();
    }

    @Override
    public void setLE16(int index, short value) {
        rejectModification();
    }

    @Override
    public void setLE24(int index, int value) {
        rejectModification();
    }

    @Override
    public void setLE32(int index, int value) {
        rejectModification();
    }

    @Override
    public void setLE48(int index, long value) {
        rejectModification();
    }

    @Override
    public void setLE64(int index, long value) {
        rejectModification();
    }

    @Override
    public void set(int index, ByteArray src) {
        rejectModification();
    }

    @Override
    public void set(int index, byte[] src) {
        rejectModification();
    }

    protected void rejectModification() {
        throw new UnsupportedOperationException("read-only");
    }
}
