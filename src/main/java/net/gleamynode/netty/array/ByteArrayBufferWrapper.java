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

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ByteArrayBufferWrapper extends ByteArrayWrapper implements
        ByteArrayBuffer {

    protected ByteArrayBufferWrapper(ByteArray array) {
        super(array);
    }

    @Override
    public ByteArrayBuffer unwrap() {
        return (ByteArrayBuffer) super.unwrap();
    }

    public ByteArray read() {
        return unwrap().read();
    }

    public ByteArray read(int length) {
        return unwrap().read(length);
    }

    public ByteArray read(ByteArrayIndexFinder endIndexFinder) {
        return unwrap().read(endIndexFinder);
    }

    public byte read8() {
        return unwrap().read8();
    }

    public short readBE16() {
        return unwrap().readBE16();
    }

    public int readBE24() {
        return unwrap().readBE24();
    }

    public int readBE32() {
        return unwrap().readBE32();
    }

    public long readBE48() {
        return unwrap().readBE48();
    }

    public long readBE64() {
        return unwrap().readBE64();
    }

    public short readLE16() {
        return unwrap().readLE16();
    }

    public int readLE24() {
        return unwrap().readLE24();
    }

    public int readLE32() {
        return unwrap().readLE32();
    }

    public long readLE48() {
        return unwrap().readLE48();
    }

    public long readLE64() {
        return unwrap().readLE64();
    }

    public void skip(int length) {
        unwrap().skip(length);
    }

    public void skip(ByteArrayIndexFinder fisrtIndexFinder) {
        unwrap().skip(fisrtIndexFinder);
    }

    public void write(byte[] src, int srcIndex, int length) {
        unwrap().write(src, srcIndex, length);
    }

    public void write(byte[] src) {
        unwrap().write(src);
    }

    public void write(ByteArray src, int srcIndex, int length) {
        unwrap().write(src, srcIndex, length);
    }

    public void write(ByteArray src) {
        unwrap().write(src);
    }

    public void write(ByteBuffer src) {
        unwrap().write(src);
    }

    public void write8(byte value) {
        unwrap().write8(value);
    }

    public void writeBE16(short value) {
        unwrap().writeBE16(value);
    }

    public void writeBE24(int value) {
        unwrap().writeBE24(value);
    }

    public void writeBE32(int value) {
        unwrap().writeBE32(value);
    }

    public void writeBE48(long value) {
        unwrap().writeBE48(value);
    }

    public void writeBE64(long value) {
        unwrap().writeBE64(value);
    }

    public void writeLE16(short value) {
        unwrap().writeLE16(value);
    }

    public void writeLE24(int value) {
        unwrap().writeLE24(value);
    }

    public void writeLE32(int value) {
        unwrap().writeLE32(value);
    }

    public void writeLE48(long value) {
        unwrap().writeLE48(value);
    }

    public void writeLE64(long value) {
        unwrap().writeLE64(value);
    }
}
