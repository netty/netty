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
package io.netty.channel.group;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ServerChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ConcurrentSet;
import io.netty.util.internal.StringUtil;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The default {@link ChannelGroup} implementation.
 */
public class DefaultChannelGroup extends AbstractSet<Channel> implements ChannelGroup {

    private static final AtomicInteger nextId = new AtomicInteger();
    private final String name;
    private final EventExecutor executor;
    private final ConcurrentSet<Channel> serverChannels = new ConcurrentSet<Channel>();
    private final ConcurrentSet<Channel> nonServerChannels = new ConcurrentSet<Channel>();
    private final ChannelFutureListener remover = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            remove(future.channel());
        }
    };

    /**
     * Creates a new group with a generated name and the provided {@link EventExecutor} to notify the
     * {@link ChannelGroupFuture}s.
     */
    public DefaultChannelGroup(EventExecutor executor) {
        this("group-0x" + Integer.toHexString(nextId.incrementAndGet()), executor);
    }

    /**
     * Creates a new group with the specified {@code name} and {@link EventExecutor} to notify the
     * {@link ChannelGroupFuture}s.  Please note that different groups can have the same name, which means no
     * duplicate check is done against group names.
     */
    public DefaultChannelGroup(String name, EventExecutor executor) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
        this.executor = executor;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isEmpty() {
        return nonServerChannels.isEmpty() && serverChannels.isEmpty();
    }

    @Override
    public int size() {
        return nonServerChannels.size() + serverChannels.size();
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Channel) {
            Channel c = (Channel) o;
            if (o instanceof ServerChannel) {
                return serverChannels.contains(c);
            } else {
                return nonServerChannels.contains(c);
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean add(Channel channel) {
        ConcurrentSet<Channel> set =
            channel instanceof ServerChannel? serverChannels : nonServerChannels;

        boolean added = set.add(channel);
        if (added) {
            channel.closeFuture().addListener(remover);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof Channel)) {
            return false;
        }
        boolean removed;
        Channel c = (Channel) o;
        if (c instanceof ServerChannel) {
            removed = serverChannels.remove(c);
        } else {
            removed = nonServerChannels.remove(c);
        }
        if (!removed) {
            return false;
        }

        c.closeFuture().removeListener(remover);
        return true;
    }

    @Override
    public void clear() {
        nonServerChannels.clear();
        serverChannels.clear();
    }

    @Override
    public Iterator<Channel> iterator() {
        return new CombinedIterator<Channel>(
                serverChannels.iterator(),
                nonServerChannels.iterator());
    }

    @Override
    public Object[] toArray() {
        Collection<Channel> channels = new ArrayList<Channel>(size());
        channels.addAll(serverChannels);
        channels.addAll(nonServerChannels);
        return channels.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Collection<Channel> channels = new ArrayList<Channel>(size());
        channels.addAll(serverChannels);
        channels.addAll(nonServerChannels);
        return channels.toArray(a);
    }

    @Override
    public ChannelGroupFuture close() {
        return close(ChannelMatchers.all());
    }

    @Override
    public ChannelGroupFuture disconnect() {
        return disconnect(ChannelMatchers.all());
    }

    @Override
    public ChannelGroupFuture deregister() {
        return deregister(ChannelMatchers.all());
    }

    @Override
    public ChannelGroupFuture write(Object message) {
        return write(message, ChannelMatchers.all());
    }

    // Create a safe duplicate of the message to write it to a channel but not affect other writes.
    // See https://github.com/netty/netty/issues/1461
    private static Object safeDuplicate(Object message) {
        if (message instanceof ByteBuf) {
            return ((ByteBuf) message).duplicate().retain();
        } else if (message instanceof ByteBufHolder) {
            return ((ByteBufHolder) message).duplicate().retain();
        } else {
            return ReferenceCountUtil.retain(message);
        }
    }

    @Override
    public ChannelGroupFuture write(Object message, ChannelMatcher matcher) {
        if (message == null) {
            throw new NullPointerException("message");
        }
        if (matcher == null) {
            throw new NullPointerException("matcher");
        }

        Map<Channel, ChannelFuture> futures = new LinkedHashMap<Channel, ChannelFuture>(size());
        for (Channel c: nonServerChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.write(safeDuplicate(message)));
            }
        }

        ReferenceCountUtil.release(message);
        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroup flush() {
        return flush(ChannelMatchers.all());
    }

    @Override
    public ChannelGroupFuture flushAndWrite(Object message) {
        return writeAndFlush(message);
    }

    @Override
    public ChannelGroupFuture writeAndFlush(Object message) {
        return writeAndFlush(message, ChannelMatchers.all());
    }

    @Override
    public ChannelGroupFuture disconnect(ChannelMatcher matcher) {
        if (matcher == null) {
            throw new NullPointerException("matcher");
        }

        Map<Channel, ChannelFuture> futures =
                new LinkedHashMap<Channel, ChannelFuture>(size());

        for (Channel c: serverChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.disconnect());
            }
        }
        for (Channel c: nonServerChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.disconnect());
            }
        }

        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroupFuture close(ChannelMatcher matcher) {
        if (matcher == null) {
            throw new NullPointerException("matcher");
        }

        Map<Channel, ChannelFuture> futures =
                new LinkedHashMap<Channel, ChannelFuture>(size());

        for (Channel c: serverChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.close());
            }
        }
        for (Channel c: nonServerChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.close());
            }
        }

        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroupFuture deregister(ChannelMatcher matcher) {
        if (matcher == null) {
            throw new NullPointerException("matcher");
        }

        Map<Channel, ChannelFuture> futures =
                new LinkedHashMap<Channel, ChannelFuture>(size());

        for (Channel c: serverChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.deregister());
            }
        }
        for (Channel c: nonServerChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.deregister());
            }
        }

        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroup flush(ChannelMatcher matcher) {
        for (Channel c: nonServerChannels) {
            if (matcher.matches(c)) {
                c.flush();
            }
        }
        return this;
    }

    @Override
    public ChannelGroupFuture flushAndWrite(Object message, ChannelMatcher matcher) {
        return writeAndFlush(message, matcher);
    }

    @Override
    public ChannelGroupFuture writeAndFlush(Object message, ChannelMatcher matcher) {
        if (message == null) {
            throw new NullPointerException("message");
        }

        Map<Channel, ChannelFuture> futures = new LinkedHashMap<Channel, ChannelFuture>(size());

        for (Channel c: nonServerChannels) {
            if (matcher.matches(c)) {
                futures.put(c, c.writeAndFlush(safeDuplicate(message)));
            }
        }

        ReferenceCountUtil.release(message);

        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int compareTo(ChannelGroup o) {
        int v = name().compareTo(o.name());
        if (v != 0) {
            return v;
        }

        return System.identityHashCode(this) - System.identityHashCode(o);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(name: " + name() + ", size: " + size() + ')';
    }
}
