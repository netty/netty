/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.unix.IovArray;

final class IovArrays {

    private final IovArray[] iovArrays;
    private int iovArrayIdx;

    IovArrays(int numArrays) {
        iovArrays = new IovArray[numArrays];
         for (int i = 0 ; i < iovArrays.length; i++) {
             iovArrays[i] = new IovArray();
         }
    }

    /**
     * Return the next {@link IovArray} to use which has space left in it. Otherwise returns {@code null} if there
     * is no {@link IovArray} which has some space left.
     */
    IovArray next() {
        IovArray iovArray = iovArrays[iovArrayIdx];
        if (iovArray.isFull()) {
            if (iovArrayIdx < iovArrays.length - 1) {
                // There is another array left that we can use, increment the index and use it.
                iovArrayIdx++;
                iovArray = iovArrays[iovArrayIdx];
            }
        }
        return iovArray.isFull() ? null : iovArray;
    }

    /**
     * Clear all {@link IovArray}s that were used since the last time this method was called.
     */
    void clear() {
        for (int i = 0; i <= iovArrayIdx; i++) {
            iovArrays[i].clear();
        }
        iovArrayIdx = 0;
    }

    void release() {
        for (IovArray array: iovArrays) {
            array.release();
        }
    }
}
