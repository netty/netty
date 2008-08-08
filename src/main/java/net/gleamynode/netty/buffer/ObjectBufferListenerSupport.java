/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.buffer;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ObjectBufferListenerSupport<E> {

    private static final Logger log =
        Logger.getLogger(ObjectBufferListenerSupport.class.getName());

    private final ObjectBuffer<E> buffer;
    private volatile ObjectBufferListener<?>[] listeners =
        new ObjectBufferListener<?>[0];

    public ObjectBufferListenerSupport(ObjectBuffer<E> buffer) {
        this.buffer = buffer;
    }

    public void addListener(ObjectBufferListener<? super E> listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        synchronized (this) {
            ObjectBufferListener<?>[] oldListeners = listeners;
            ObjectBufferListener<?>[] newListeners =
                new ObjectBufferListener<?>[oldListeners.length + 1];
            System.arraycopy(oldListeners, 0, newListeners, 0, oldListeners.length);
            newListeners[oldListeners.length] = listener;
            listeners = newListeners;
        }
    }

    public void removeListener(ObjectBufferListener<? super E> listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        synchronized (this) {
            ObjectBufferListener<?>[] oldListeners = listeners;
            int listenerIndex = -1;
            for (int i = 0; i < oldListeners.length; i ++) {
                if (oldListeners[i] == listener) {
                    listenerIndex = i;
                    break;
                }
            }

            if (listenerIndex >= 0) {
                ObjectBufferListener<?>[] newListeners =
                    new ObjectBufferListener<?>[listeners.length - 1];

                int i = 0;
                boolean removed = false;
                for (ObjectBufferListener<?> l : oldListeners) {
                    if (!removed) {
                        if (i == listenerIndex) {
                            removed = true;
                        } else {
                            newListeners[i ++] = l;
                        }
                    } else {
                        newListeners[i ++] = l;
                    }
                }

                listeners = newListeners;
            }
        }
    }

    public void assureAcceptance(E item) {
        ObjectBufferListener<Object>[] listeners = getListeners();
        for (ObjectBufferListener<Object> l: listeners) {
            try {
                if (!l.acceptElement(buffer, item)) {
                    for (ObjectBufferListener<Object> l2: listeners) {
                        try {
                            l2.elementUnaccepted(buffer, item);
                        } catch (Throwable t) {
                            log.log(Level.WARNING,
                                    "An exception was raised by an " +
                                    "ObjectBufferListener.", t);
                        }
                    }
                    return;
                }
            } catch (Throwable t) {
                log.log(Level.WARNING,
                        "An exception was raised by an ObjectBufferListener.",
                        t);
            }
        }
    }

    public void notifyAddition(E item) {
        for(ObjectBufferListener<Object> l: getListeners()) {
            try {
                l.elementAdded(buffer, item);
            } catch (Throwable t) {
                log.log(Level.WARNING,
                        "An exception was raised by an ObjectBufferListener.",
                        t);
            }
        }
    }
    public void notifyRemoval(E item) {
        for(ObjectBufferListener<Object> l: getListeners()) {
            try {
                l.elementRemoved(buffer, item);
            } catch (Throwable t) {
                log.log(Level.WARNING,
                        "An exception was raised by an ObjectBufferListener.",
                        t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ObjectBufferListener<Object>[] getListeners() {
        return (ObjectBufferListener<Object>[]) listeners;
    }
}
