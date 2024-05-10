/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.uring;

import java.util.function.ObjLongConsumer;

final class ObjectRing<T> implements ObjLongConsumer<T> {
    private Object[] objs;
    private long[] stamps;
    private int head; // next push index
    private int tail; // next poll index, unless same as head
    private Object polledObject;
    private long polledStamp;

    ObjectRing() {
        objs = new Object[16];
        stamps = new long[16];
    }

    @Override
    public void accept(T obj, long value) {
        push(obj, value);
    }

    void push(T obj, long stamp) {
        int nextHead = next(head);
        if (nextHead == tail) {
            expand();
            nextHead = next(head);
        }
        objs[head] = obj;
        stamps[head] = stamp;
        head = nextHead;
    }

    boolean isEmpty() {
        return tail == head;
    }

    @SuppressWarnings("unchecked")
    T remove(long stamp) {
        if (isEmpty()) {
            return null;
        }
        if (stamps[tail] == stamp) {
            Object obj = objs[tail];
            objs[tail] = null;
            stamps[tail] = 0;
            tail = next(tail);
            return (T) obj;
        }
        // iterate and do internal remove
        int index = tail;
        while (index != head) {
            if (stamps[index] == stamp) {
                Object obj = objs[index];
                // Found the obj; move prior entries up.
                int i = tail;
                Object prevObj = objs[tail];
                long prevStamp = stamps[tail];
                do {
                    i = next(i);
                    Object tmpObj = objs[i];
                    long tmpStamp = stamps[i];
                    objs[i] = prevObj;
                    stamps[i] = prevStamp;
                    prevObj = tmpObj;
                    prevStamp = tmpStamp;
                } while (i != index);
                tail = next(tail);
                return (T) obj;
            }
            index = next(index);
        }
        return null;
    }

    boolean poll() {
        if (isEmpty()) {
            return false;
        }
        polledObject = objs[tail];
        polledStamp = stamps[tail];
        objs[tail] = null;
        stamps[tail] = 0;
        tail = next(tail);
        return true;
    }

    boolean peek() {
        if (isEmpty()) {
            return false;
        }
        polledObject = objs[tail];
        polledStamp = stamps[tail];
        return true;
    }

    void updatePeekedStamp(long stamp) {
        if (isEmpty()) {
            throw new IllegalStateException("No peeked stamp");
        }
        stamps[tail] = stamp;
    }

    boolean hasNextStamp(long stamp) {
        return !isEmpty() && stamps[tail] == stamp;
    }

    @SuppressWarnings("unchecked")
    T getPolledObject() {
        T object = (T) polledObject;
        polledObject = null;
        return object;
    }

    long getPolledStamp() {
        long id = polledStamp;
        polledStamp = 0;
        return id;
    }

    private void expand() {
        Object[] nextBufs = new Object[objs.length << 1];
        long[] nextStamps = new long[stamps.length << 1];
        int index = 0;
        while (head != tail) {
            nextBufs[index] = objs[tail];
            nextStamps[index] = stamps[tail];
            index++;
            tail = next(tail);
        }
        objs = nextBufs;
        stamps = nextStamps;
        tail = 0;
        head = index;
    }

    private int next(int index) {
        if (index + 1 == objs.length) {
            return 0;
        }
        return index + 1;
    }
}
