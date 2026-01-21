/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import java.util.Arrays;

final class DefaultFutureListenersAndHandlers {

    private Object[] listenersAndHandlers;
    private int size;

    DefaultFutureListenersAndHandlers(
            Object first, Object second) {
        listenersAndHandlers = new Object[2];
        listenersAndHandlers[0] = first;
        listenersAndHandlers[1] = second;
        size = 2;
    }

    void add(Object l) {
        Object[] listeners = this.listenersAndHandlers;
        final int size = this.size;
        if (size == listeners.length) {
            this.listenersAndHandlers = listeners = Arrays.copyOf(listeners, size << 1);
        }
        listeners[size] = l;
        this.size = size + 1;
    }

    Object[] listenersAndHandlers() {
        return listenersAndHandlers;
    }

    int size() {
        return size;
    }
}
