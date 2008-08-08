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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;

import net.gleamynode.netty.array.ByteArray;


public class SynchronizedByteArrayBuffer implements ByteArrayBuffer {

    private final ByteArrayBuffer buffer;

    public SynchronizedByteArrayBuffer(ByteArrayBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
    }

    public void addListener(ObjectBufferListener<? super ByteArray> listener) {
        buffer.addListener(listener);
    }

    public void removeListener(ObjectBufferListener<? super ByteArray> listener) {
        buffer.removeListener(listener);
    }

    public synchronized int count() {
        return buffer.count();
    }

    public synchronized ByteArray elementAt(int index) {
        return buffer.elementAt(index);
    }

    public synchronized boolean empty() {
        return buffer.empty();
    }

    public synchronized int endIndex() {
        return buffer.endIndex();
    }

    public synchronized int firstIndex() {
        return buffer.firstIndex();
    }

    public synchronized void get(int index, byte[] dst, int dstIndex, int length) {
        buffer.get(index, dst, dstIndex, length);
    }

    public synchronized void get(int index, byte[] dst) {
        buffer.get(index, dst);
    }

    public synchronized void get(int index, ByteArray dst, int dstIndex, int length) {
        buffer.get(index, dst, dstIndex, length);
    }

    public synchronized void get(int index, ByteArray dst) {
        buffer.get(index, dst);
    }

    public synchronized void get(int index, ByteBuffer dst) {
        buffer.get(index, dst);
    }

    public synchronized byte get8(int index) {
        return buffer.get8(index);
    }

    public synchronized short getBE16(int index) {
        return buffer.getBE16(index);
    }

    public synchronized int getBE24(int index) {
        return buffer.getBE24(index);
    }

    public synchronized int getBE32(int index) {
        return buffer.getBE32(index);
    }

    public synchronized long getBE48(int index) {
        return buffer.getBE48(index);
    }

    public synchronized long getBE64(int index) {
        return buffer.getBE64(index);
    }

    public synchronized short getLE16(int index) {
        return buffer.getLE16(index);
    }

    public synchronized int getLE24(int index) {
        return buffer.getLE24(index);
    }

    public synchronized int getLE32(int index) {
        return buffer.getLE32(index);
    }

    public synchronized long getLE48(int index) {
        return buffer.getLE48(index);
    }

    public synchronized long getLE64(int index) {
        return buffer.getLE64(index);
    }

    public synchronized Iterator<ByteArray> iterator() {
        return buffer.iterator();
    }

    public synchronized int length() {
        return buffer.length();
    }

    public synchronized ByteArray read() {
        return buffer.read();
    }

    public synchronized ByteArray read(int length) {
        return buffer.read(length);
    }

    public synchronized byte read8() {
        return buffer.read8();
    }

    public synchronized short readBE16() {
        return buffer.readBE16();
    }

    public synchronized int readBE24() {
        return buffer.readBE24();
    }

    public synchronized int readBE32() {
        return buffer.readBE32();
    }

    public synchronized long readBE48() {
        return buffer.readBE48();
    }

    public synchronized long readBE64() {
        return buffer.readBE64();
    }

    public synchronized short readLE16() {
        return buffer.readLE16();
    }

    public synchronized int readLE24() {
        return buffer.readLE24();
    }

    public synchronized int readLE32() {
        return buffer.readLE32();
    }

    public synchronized long readLE48() {
        return buffer.readLE48();
    }

    public synchronized long readLE64() {
        return buffer.readLE64();
    }

    public synchronized void set(int index, byte[] src, int srcIndex, int length) {
        buffer.set(index, src, srcIndex, length);
    }

    public synchronized void set(int index, byte[] src) {
        buffer.set(index, src);
    }

    public synchronized void set(int index, ByteArray src, int srcIndex, int length) {
        buffer.set(index, src, srcIndex, length);
    }

    public synchronized void set(int index, ByteArray src) {
        buffer.set(index, src);
    }

    public synchronized void set(int index, ByteBuffer src) {
        buffer.set(index, src);
    }

    public synchronized void set8(int index, byte value) {
        buffer.set8(index, value);
    }

    public synchronized void setBE16(int index, short value) {
        buffer.setBE16(index, value);
    }

    public synchronized void setBE24(int index, int value) {
        buffer.setBE24(index, value);
    }

    public synchronized void setBE32(int index, int value) {
        buffer.setBE32(index, value);
    }

    public synchronized void setBE48(int index, long value) {
        buffer.setBE48(index, value);
    }

    public synchronized void setBE64(int index, long value) {
        buffer.setBE64(index, value);
    }

    public synchronized void setLE16(int index, short value) {
        buffer.setLE16(index, value);
    }

    public synchronized void setLE24(int index, int value) {
        buffer.setLE24(index, value);
    }

    public synchronized void setLE32(int index, int value) {
        buffer.setLE32(index, value);
    }

    public synchronized void setLE48(int index, long value) {
        buffer.setLE48(index, value);
    }

    public synchronized void setLE64(int index, long value) {
        buffer.setLE64(index, value);
    }

    public synchronized void skip(int length) {
        buffer.skip(length);
    }

    public synchronized void write(byte[] src, int srcIndex, int length) {
        buffer.write(src, srcIndex, length);
    }

    public synchronized void write(byte[] src) {
        buffer.write(src);
    }

    public synchronized void write(ByteArray src, int srcIndex, int length) {
        buffer.write(src, srcIndex, length);
    }

    public synchronized void write(ByteArray src) {
        buffer.write(src);
    }

    public synchronized void write(ByteBuffer src) {
        buffer.write(src);
    }

    public synchronized void write8(byte value) {
        buffer.write8(value);
    }

    public synchronized void writeBE16(short value) {
        buffer.writeBE16(value);
    }

    public synchronized void writeBE24(int value) {
        buffer.writeBE24(value);
    }

    public synchronized void writeBE32(int value) {
        buffer.writeBE32(value);
    }

    public synchronized void writeBE48(long value) {
        buffer.writeBE48(value);
    }

    public synchronized void writeBE64(long value) {
        buffer.writeBE64(value);
    }

    public synchronized void writeLE16(short value) {
        buffer.writeLE16(value);
    }

    public synchronized void writeLE24(int value) {
        buffer.writeLE24(value);
    }

    public synchronized void writeLE32(int value) {
        buffer.writeLE32(value);
    }

    public synchronized void writeLE48(long value) {
        buffer.writeLE48(value);
    }

    public synchronized void writeLE64(long value) {
        buffer.writeLE64(value);
    }

    public synchronized void copyTo(OutputStream out) throws IOException {
        buffer.copyTo(out);
    }

    public synchronized void copyTo(OutputStream out, int index, int length)
            throws IOException {
        buffer.copyTo(out, index, length);
    }

    public int copyTo(WritableByteChannel out) throws IOException {
        return buffer.copyTo(out);
    }

    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        return buffer.copyTo(out, index, length);
    }
}
