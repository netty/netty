/*
 * Copyright 2013 The Netty Project
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

package io.netty.util.concurrent;

import java.util.Arrays;
import java.util.EventListener;

final class DefaultPromiseListeners {
    private GenericFutureListener<? extends Future<?>>[] listeners;
    private int size;

    @SuppressWarnings("unchecked")
    DefaultPromiseListeners(GenericFutureListener<? extends Future<?>> firstListener,
                            GenericFutureListener<? extends Future<?>> secondListener) {

        listeners = new GenericFutureListener[] { firstListener, secondListener };
        size = 2;
    }

    void add(GenericFutureListener<? extends Future<?>> l) {
        GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        final int size = this.size;
        if (size == listeners.length) {
            this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
        }
        listeners[size] = l;
        this.size = size + 1;
    }

    void remove(EventListener l) {
        final EventListener[] listeners = this.listeners;
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            if (listeners[i] == l) {
                int listenersToMove = size - i - 1;
                if (listenersToMove > 0) {
                    System.arraycopy(listeners, i + 1, listeners, i, listenersToMove);
                }
                listeners[-- size] = null;
                this.size = size;
                return;
            }
        }
    }

    GenericFutureListener<? extends Future<?>>[] listeners() {
        return listeners;
    }

    int size() {
        return size;
    }
}
