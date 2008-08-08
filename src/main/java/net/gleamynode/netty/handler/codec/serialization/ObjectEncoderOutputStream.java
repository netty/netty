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
package net.gleamynode.netty.handler.codec.serialization;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteArrayOutputStream;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ObjectEncoderOutputStream extends OutputStream implements
        ObjectOutput {

    private final DataOutputStream out;
    private final int capacityIncrement;

    public ObjectEncoderOutputStream(OutputStream out, int capacityIncrement) {
        if (out == null) {
            throw new NullPointerException("out");
        }
        if (capacityIncrement < 8) {
            throw new IllegalArgumentException("capacityIncrement: " + capacityIncrement);
        }

        if (out instanceof DataOutputStream) {
            this.out = (DataOutputStream) out;
        } else {
            this.out = new DataOutputStream(out);
        }
        this.capacityIncrement = capacityIncrement;
    }

    public void writeObject(Object obj) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(capacityIncrement, false);
        ObjectOutputStream oout = new CompactObjectOutputStream(bout);
        oout.writeObject(obj);
        oout.flush();
        oout.close();
    
        ByteArray array = bout.array();
        writeInt(array.length());
        array.copyTo(this);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    public final int size() {
        return out.size();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    public final void writeBoolean(boolean v) throws IOException {
        out.writeBoolean(v);
    }

    public final void writeByte(int v) throws IOException {
        out.writeByte(v);
    }

    public final void writeBytes(String s) throws IOException {
        out.writeBytes(s);
    }

    public final void writeChar(int v) throws IOException {
        out.writeChar(v);
    }

    public final void writeChars(String s) throws IOException {
        out.writeChars(s);
    }

    public final void writeDouble(double v) throws IOException {
        out.writeDouble(v);
    }

    public final void writeFloat(float v) throws IOException {
        out.writeFloat(v);
    }

    public final void writeInt(int v) throws IOException {
        out.writeInt(v);
    }

    public final void writeLong(long v) throws IOException {
        out.writeLong(v);
    }

    public final void writeShort(int v) throws IOException {
        out.writeShort(v);
    }

    public final void writeUTF(String str) throws IOException {
        out.writeUTF(str);
    }
}
