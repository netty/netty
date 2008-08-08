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
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;



public interface ByteArray extends Comparable<ByteArray> {

    static ByteArray EMPTY_BUFFER = new HeapByteArray(0);
    static long NOT_FOUND = Long.MIN_VALUE;

    int firstIndex();
    int endIndex();
    int length();
    boolean empty();

    byte  get8   (int index);
    short getBE16(int index);
    int   getBE24(int index);
    int   getBE32(int index);
    long  getBE48(int index);
    long  getBE64(int index);
    short getLE16(int index);
    int   getLE24(int index);
    int   getLE32(int index);
    long  getLE48(int index);
    long  getLE64(int index);

    void get(int index, ByteArray dst);
    void get(int index, ByteArray dst, int dstIndex, int length);
    void get(int index, byte[] dst);
    void get(int index, byte[] dst, int dstIndex, int length);
    void get(int index, ByteBuffer dst);

    void set8   (int index, byte  value);
    void setBE16(int index, short value);
    void setBE24(int index, int   value);
    void setBE32(int index, int   value);
    void setBE48(int index, long  value);
    void setBE64(int index, long  value);
    void setLE16(int index, short value);
    void setLE24(int index, int   value);
    void setLE32(int index, int   value);
    void setLE48(int index, long  value);
    void setLE64(int index, long  value);

    void set(int index, ByteArray src);
    void set(int index, ByteArray src, int srcIndex, int length);
    void set(int index, byte[] src);
    void set(int index, byte[] src, int srcIndex, int length);
    void set(int index, ByteBuffer src);

    long indexOf(int fromIndex, byte value);
    long indexOf(int fromIndex, ByteArrayIndexFinder indexFinder);
    long lastIndexOf(int fromIndex, byte value);
    long lastIndexOf(int fromIndex, ByteArrayIndexFinder indexFinder);

    void copyTo(OutputStream out) throws IOException;
    void copyTo(OutputStream out, int index, int length) throws IOException;
    int  copyTo(WritableByteChannel out) throws IOException;
    int  copyTo(WritableByteChannel out, int index, int length) throws IOException;
    int  copyTo(GatheringByteChannel out) throws IOException;
    int  copyTo(GatheringByteChannel out, int index, int length) throws IOException;

    ByteBuffer getByteBuffer();
    ByteBuffer getByteBuffer(int index, int length);
}
