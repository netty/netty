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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.CompositeByteArray;
import net.gleamynode.netty.array.DirectByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.array.PartialByteArray;


public class CompositeByteArrayTest extends AbstractByteArrayTest {

    private static final int COMPONENT_MAX_LENGTH = 256;
    private final Random random = new Random();
    private final List<ByteArray> arrays = new ArrayList<ByteArray>();

    @Override
    protected ByteArray newArray(int length) {
        arrays.clear();
        CompositeByteArray ca = new CompositeByteArray();
        while (ca.length() != length) {
            int aLen = random.nextInt(COMPONENT_MAX_LENGTH) + 1;
            if (ca.length() + aLen > length) {
                aLen = length - ca.length();
            }

            ByteArray a;
            if (random.nextBoolean()) {
                a = new PartialByteArray(
                        new HeapByteArray(COMPONENT_MAX_LENGTH * 2),
                        random.nextInt(COMPONENT_MAX_LENGTH), aLen);
            } else {
                a = new PartialByteArray(
                        new DirectByteArray(COMPONENT_MAX_LENGTH * 2),
                        random.nextInt(COMPONENT_MAX_LENGTH), aLen);
            }

            if (random.nextBoolean()) {
                ca.addFirst(a);
                arrays.add(0, a);
            } else {
                ca.addLast(a);
                arrays.add(a);
            }
        }
        return ca;
    }

    @Override
    protected ByteArray[] components() {
        return arrays.toArray(new ByteArray[arrays.size()]);
    }
}
