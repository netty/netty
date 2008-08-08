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
package net.gleamynode.netty.buffer;

import java.nio.ByteBuffer;

import net.gleamynode.netty.array.ByteArray;

public interface ByteArrayBuffer extends
                                 ObjectBuffer<ByteArray>, ByteArray {
    byte  read8   ();
    short readBE16();
    int   readBE24();
    int   readBE32();
    long  readBE48();
    long  readBE64();
    short readLE16();
    int   readLE24();
    int   readLE32();
    long  readLE48();
    long  readLE64();

    ByteArray read(int length);
    void skip(int length);

    void write8   (byte  value);
    void writeBE16(short value);
    void writeBE24(int   value);
    void writeBE32(int   value);
    void writeBE48(long  value);
    void writeBE64(long  value);
    void writeLE16(short value);
    void writeLE24(int   value);
    void writeLE32(int   value);
    void writeLE48(long  value);
    void writeLE64(long  value);

    void write(ByteArray src);
    void write(ByteArray src, int srcIndex, int length);
    void write(byte[] src);
    void write(byte[] src, int srcIndex, int length);
    void write(ByteBuffer src);
}
