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

import io.netty.util.collection.ShortObjectHashMap;

import java.util.function.BiConsumer;

interface PendingData<D> {
    static <V> PendingData<V> newPendingData() {
        final class PendingDataMap<D> extends ShortObjectHashMap<D> implements PendingData<D> {
            private int counter;

            @Override
            public short addPending(D data) {
                short id = nextPendingId();
                put(id, data);
                return id;
            }

            @Override
            public D removePending(short id) {
                return remove(id);
            }

            private short nextPendingId() {
                if (size() > Short.MAX_VALUE) {
                    throw new IllegalStateException("Too many pending objects: " + size());
                }
                short candidate;
                do {
                    candidate = (short) ++counter;
                } while (candidate == 0 || containsKey(candidate)); // Avoid using 0 for id; it can cause type confusion
                return candidate;
            }

            @Override
            public void forEach(BiConsumer<? super Short, ? super D> action) {
                super.forEach(action);
            }
        }
        return new PendingDataMap<>();
    }

    short addPending(D data);

    D removePending(short id);

    boolean isEmpty();

    void forEach(BiConsumer<? super Short, ? super D> consumer);
}
