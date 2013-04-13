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

public final class DefaultEventListeners {
    private EventListener[] listeners;
    private int size;

    public DefaultEventListeners(EventListener firstListener, EventListener secondListener) {
        listeners = new EventListener[2];
        listeners [0] = firstListener;
        listeners [1] = secondListener;
        size = 2;
    }

    public void add(EventListener t) {
        EventListener[] listeners = this.listeners;
        final int size = this.size;
        if (size == listeners.length) {
            this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
        }
        listeners[size] = t;
        this.size = size + 1;
    }

    public void remove(EventListener t) {
        final EventListener[] listeners = this.listeners;
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            if (listeners[i] == t) {
                int listenersToMove = size - i - 1;
                if (listenersToMove > 0) {
                    System.arraycopy(listeners, i + 1, listeners, i, listenersToMove);
                }
                listeners[-- size] = null;
                this.size = size;
                return;
            }
        }    }

    public EventListener[] listeners() {
        return listeners;
    }

    public int size() {
        return size;
    }
}
