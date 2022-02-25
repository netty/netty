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
package io.netty5.util.concurrent;

import io.netty5.util.internal.logging.InternalLogger;

import java.util.Arrays;
import java.util.EventListener;

final class DefaultFutureListeners {
    private Object[] listeners;
    private int size;

    DefaultFutureListeners() {
        listeners = new Object[4];
    }

    public void add(Object listener, Object context) {
        Object[] listeners = this.listeners;
        int index = size << 1;
        if (index == listeners.length) {
            this.listeners = listeners = Arrays.copyOf(listeners, listeners.length << 1);
        }
        listeners[index] = listener;
        listeners[index + 1] = context;
        size++;
    }

    public void remove(EventListener listener) {
        final Object[] listeners = this.listeners;
        for (int i = 0, len = listeners.length; i < len; i += 2) {
            Object candidateListener = listeners[i]; // With associated context at listeners[i + 1]
            if (candidateListener == listener) {
                int listenersToMove = len - i - 2;
                if (listenersToMove > 0) {
                    System.arraycopy(listeners, i + 2, listeners, i, listenersToMove);
                }
                listeners[len - 2] = null;
                listeners[len - 1] = null;
                size--;
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <V> void notifyListeners(DefaultPromise<V> promise, InternalLogger logger) {
        int size = this.size;
        Object[] listeners = this.listeners;
        for (int i = 0, len = size << 1; i < len; i += 2) {
            Object listener = listeners[i];
            Object context = listeners[i + 1];
            try {
                // Since a listener could in theory be both a FutureListener and a FutureContextListener,
                // we use the presence of a context object to determine which one was meant when the listener
                // was added. The context reference will never be null if the FutureContextListener was intended,
                // even if the context passed was null. In that case, the reference will point to the
                // NULL_CONTEXT, and we have to convert it back to null here.
                if (context != null) {
                    FutureContextListener<Object, V> fcl = (FutureContextListener<Object, V>) listener;
                    fcl.operationComplete(context == DefaultPromise.NULL_CONTEXT ? null : context, promise);
                } else if (listener instanceof FutureListener) {
                    FutureListener<V> fl = (FutureListener<V>) listener;
                    fl.operationComplete(promise);
                } else if (listener != null) {
                    logger.warn("Unknown future listener type: {} of type {}", listener, listener.getClass());
                } else {
                    break; // Listeners are always densely packed in the array, so finding a null means we're done.
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
