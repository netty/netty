/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel;

import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.util.ConcurrentWeakHashMap;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.stereotype utility
 */
public class ChannelLocal<T> {
    private final ConcurrentMap<Channel, T> map = new ConcurrentWeakHashMap<Channel, T>();

    /**
     * Creates a {@link Channel} local variable.
     */
    public ChannelLocal() {
        super();
    }

    protected T initialValue(@SuppressWarnings("unused") Channel channel) {
        return null;
    }

    public T get(Channel channel) {
        T value = map.get(channel);
        if (value == null) {
            value = initialValue(channel);
            T oldValue = setIfAbsent(channel, value);
            if (oldValue != null) {
                value = oldValue;
            }
        }
        return value;
    }

    public T set(Channel channel, T newValue) {
        return map.put(channel, newValue);
    }

    public T setIfAbsent(Channel channel, T value) {
        return map.putIfAbsent(channel, value);
    }

    public T remove(Channel channel) {
        return map.remove(channel);
    }
}
