/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;

import io.netty.util.internal.ConcurrentIdentityWeakKeyHashMap;

/**
 * A global variable that is local to a {@link Channel}.  Think of this as a
 * variation of {@link ThreadLocal} whose key is a {@link Channel} rather than
 * a {@link Thread#currentThread()}.  One difference is that you always have to
 * specify the {@link Channel} to access the variable.
 * <p>
 * Alternatively, you might want to use the
 * {@link ChannelHandlerContext#setAttachment(Object) ChannelHandlerContext.attachment}
 * property, which performs better.
 * @deprecated Use {@link Channel#setAttachment(Object)} and {@link Channel#getAttachment()}
 * 
 * @apiviz.stereotype utility
 */
@Deprecated
public class ChannelLocal<T> implements Iterable<Entry<Channel, T>> {

    private final ConcurrentMap<Channel, T> map =
        new ConcurrentIdentityWeakKeyHashMap<Channel, T>();

    private final ChannelFutureListener remover = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            remove(future.getChannel());
        }
    };

    private final boolean removeOnClose;
    
    /**
     * Creates a {@link Channel} local variable by calling {@link #ChannelLocal(boolean)} with <code>true</code>
     */
    public ChannelLocal() {
        this(true);
    }
    
    /**
     * Creates a {@link Channel} local variable. 
     * 
     * @param removeOnClose if <code>true</code> the {@link ChannelLocal} will remove a {@link Channel} from it own once the {@link Channel} was closed.
     */
    public ChannelLocal(boolean removeOnClose) {
        this.removeOnClose = removeOnClose;
    }
    


    /**
     * Returns the initial value of the variable.  By default, it returns
     * {@code null}.  Override it to change the initial value.
     */
    protected T initialValue(Channel channel) {
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
            T old =  map.put(channel, value);
            if (removeOnClose) {
                channel.getCloseFuture().addListener(remover);
            }
            return old;
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
            T mapping =  map.putIfAbsent(channel, value);
            
            if (removeOnClose && mapping == null) {
                channel.getCloseFuture().addListener(remover);
            }
            return mapping;
        }
    }

    /**
     * Removes the variable and returns the removed value.  If no value was set,
     * this method returns the return value of {@link #initialValue(Channel)},
     * which is {@code null} by default.
     *
     * @return the removed value.
     *         {@linkplain #initialValue(Channel) an initial value} (by default
     *         {@code null}) if no value was set.
     */
    public T remove(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        T removed = map.remove(channel);
        if (removed == null) {
            return initialValue(channel);
        } else {
            if (removeOnClose) {
                channel.getCloseFuture().removeListener(remover);
            }
            return removed;
        }
    }

    /**
     * Returns a <strong>read-only</strong> {@link Iterator} that holds all {@link Entry}'s of this ChannelLocal
     */
    public Iterator<Entry<Channel, T>> iterator() {
        return Collections.unmodifiableSet(map.entrySet()).iterator();
    }
}
