/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
 * Alternatively, you might want to use the
 * {@link ChannelHandlerContext#setAttachment(Object) ChannelHandlerContext.attachment}
 * property, which performs better.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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
