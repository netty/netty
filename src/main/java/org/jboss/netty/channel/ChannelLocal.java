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

import org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap;

/**
 * A global variable that is local to a {@link Channel}.  Think of this as a
 * variation of {@link ThreadLocal} whose key is a {@link Channel} rather than
 * a {@link Thread#currentThread()}.  One difference is that you always have to
 * specify the {@link Channel} to access the variable.
 * <p>
 * As an alternative, you might want to use an attachment storage provided by
 * {@link ChannelHandlerContext}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.stereotype utility
 */
public class ChannelLocal<T> {

    private final ConcurrentMap<Channel, T> map =
        new ConcurrentIdentityWeakKeyHashMap<Channel, T>();

    /**
     * Creates a {@link Channel} local variable.
     */
    public ChannelLocal() {
        super();
    }

    /**
     * Returns the initial value of the variable.  By default, it returns
     * {@code null}.  Override it to change the initial value.
     */
    protected T initialValue(@SuppressWarnings("unused") Channel channel) {
        return null;
    }

    /**
     * Returns the value of this variable.
     */
    public T get(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }

        T value = map.get(channel);
        if (value == null) {
            value = initialValue(channel);
            if (value != null) {
                T oldValue = setIfAbsent(channel, value);
                if (oldValue != null) {
                    value = oldValue;
                }
            }
        }
        return value;
    }

    /**
     * Sets the value of this variable.
     *
     * @return the old value. {@code null} if there was no old value.
     */
    public T set(Channel channel, T value) {
        if (value == null) {
            return remove(channel);
        } else {
            if (channel == null) {
                throw new NullPointerException("channel");
            }
            return map.put(channel, value);
        }
    }

    /**
     * Sets the value of this variable only when no value was set.
     *
     * @return {@code null} if the specified value was set.
     *         An existing value if failed to set.
     */
    public T setIfAbsent(Channel channel, T value) {
        if (value == null) {
            return get(channel);
        } else {
            if (channel == null) {
                throw new NullPointerException("channel");
            }
            return map.putIfAbsent(channel, value);
        }
    }

    /**
     * Removes the variable.
     *
     * @return the removed value. {@code null} if no value was set.
     */
    public T remove(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        return map.remove(channel);
    }
}
