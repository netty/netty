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

import io.netty.util.internal.logging.InternalLogger;

import java.util.Arrays;
import java.util.EventListener;

final class DefaultFutureListeners {
    private Object[] listeners;
    private int size;

    DefaultFutureListeners() {
        listeners = new Object[4];
    }

    public void add(EventListener listener, Object context) {
        Object[] listeners = this.listeners;
        int size = this.size;
        if (size == listeners.length || listener instanceof FutureContextListener && size == listeners.length - 1) {
            this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
        }
        listeners[size++] = listener;
        if (listener instanceof FutureContextListener) {
            listeners[size++] = context;
        }
        this.size = size;
    }

    public void remove(EventListener listener) {
        final Object[] listeners = this.listeners;
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            if (listeners[i] == listener) {
                int includeContext = listener instanceof FutureContextListener ? 1 : 0;
                int listenersToMove = size - i - 1 - includeContext;
                if (listenersToMove > 0) {
                    System.arraycopy(listeners, i + 1 + includeContext, listeners, i, listenersToMove);
                }
                size = size - 1 - includeContext;
                for (int j = size - 1; j < this.size; j++) {
                    listeners[j] = null;
                }
                this.size = size;
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <V> void notifyListeners(DefaultPromise<V> promise, InternalLogger logger) {
        int size = this.size;
        Object[] listeners = this.listeners;
        for (int i = 0; i < size; i++) {
            EventListener listener = (EventListener) listeners[i];
            try {
                if (listener instanceof FutureContextListener) {
                    Object context = listeners[++i];
                    FutureContextListener<Object, V> fcl = (FutureContextListener<Object, V>) listener;
                    fcl.operationComplete(context, promise);
                } else if (listener instanceof FutureListener) {
                    FutureListener<V> fl = (FutureListener<V>) listener;
                    fl.operationComplete(promise);
                } else if (listener != null) {
                    logger.warn("Unknown future listener type: {} of type {}", listener, listener.getClass());
                }
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    String className = listener.getClass().getName();
                    logger.warn("An exception was thrown by " + className + ".operationComplete()", t);
                }
            }
        }
    }
}
