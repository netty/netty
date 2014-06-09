/*
* Copyright 2014 The Netty Project
*
* The Netty Project licenses this file to you under the Apache License,
* version 2.0 (the "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at:
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/
package io.netty.util.internal;

import java.util.Arrays;

/**
 * To utilise the {@link FastThreadLocal} fast-path, all threads accessing a {@link FastThreadLocal} must extend this
 * class.
 */
public class FastThreadLocalThread extends Thread {

    Object[] lookup = newArray();

    public FastThreadLocalThread() { }

    public FastThreadLocalThread(Runnable target) {
        super(target);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public FastThreadLocalThread(String name) {
        super(name);
    }

    public FastThreadLocalThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public FastThreadLocalThread(Runnable target, String name) {
        super(target, name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    private static Object[] newArray() {
        Object[] array = new Object[32];
        Arrays.fill(array, FastThreadLocal.EMPTY);
        return array;
    }

    Object[] expandArray(int length) {
        int newCapacity = lookup.length;
        do {
            // double capacity until it is big enough
            newCapacity <<= 1;

            if (newCapacity < 0) {
                throw new IllegalStateException();
            }

        } while (length > newCapacity);

        Object[] array = new Object[newCapacity];
        System.arraycopy(lookup, 0, array, 0, lookup.length);
        Arrays.fill(array, lookup.length, array.length, FastThreadLocal.EMPTY);
        lookup = array;
        return lookup;
    }
}
