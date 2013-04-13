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

package io.netty.channel;


import java.util.Arrays;
import java.util.EventListener;

final class DefaultTransferListeners {
    private TransferFutureListener[] listeners;
    private int size;

    DefaultTransferListeners(TransferFutureListener firstListener,
                             TransferFutureListener secondListener) {
        this.listeners = new TransferFutureListener[] { firstListener, secondListener };
        this.size = 2;
    }

    DefaultTransferListeners(TransferFutureListener[] listeners) {
        this.listeners = listeners;
        this.size = listeners.length;
    }

    DefaultTransferListeners(TransferFutureListener listener) {
        this.listeners = new TransferFutureListener[] { listener };
        this.size = 1;
    }

    DefaultTransferListeners() {
        this.listeners = new TransferFutureListener[1];
        this.size = 0;
    }

    DefaultTransferListeners(int initialSize) {
        if (initialSize <= 0) {
            throw new IllegalArgumentException("initial size can not be zero or less");
        }
        this.listeners = new TransferFutureListener[initialSize];
        this.size = 0;
    }

    void add(TransferFutureListener l) {
        TransferFutureListener[] listeners = this.listeners;
        final int size = this.size;
        if (size == listeners.length) {
            this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
        }
        listeners[size] = l;
        this.size++;
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

    TransferFutureListener[] listeners() {
        return listeners;
    }

    int size() {
        return size;
    }

}
