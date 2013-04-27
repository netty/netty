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

import io.netty.buffer.BufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.FileRegion;
import io.netty.channel.ServerChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The default {@link ChannelGroup} implementation.
 */
public class DefaultChannelGroup extends AbstractSet<Channel> implements ChannelGroup {

    private static final AtomicInteger nextId = new AtomicInteger();
    private static final ImmediateEventExecutor DEFAULT_EXECUTOR = new ImmediateEventExecutor();
    private final String name;
    private final EventExecutor executor;
    private final ConcurrentMap<Integer, Channel> serverChannels = PlatformDependent.newConcurrentHashMap();
    private final ConcurrentMap<Integer, Channel> nonServerChannels = PlatformDependent.newConcurrentHashMap();
    private final ChannelFutureListener remover = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            remove(future.channel());
        }
    };

    /**
     * Creates a new group with a generated name.
     */
    public DefaultChannelGroup() {
        this("group-0x" + Integer.toHexString(nextId.incrementAndGet()));
    }

    /**
     * Creates a new group with a generated name amd the provided {@link EventExecutor} to notify the
     * {@link ChannelGroupFuture}s.
     */
    public DefaultChannelGroup(EventExecutor executor) {
        this("group-0x" + Integer.toHexString(nextId.incrementAndGet()), executor);
    }

    /**
     * Creates a new group with the specified {@code name}.  Please note that
     * different groups can have the same name, which means no duplicate check
     * is done against group names.
     */
    public DefaultChannelGroup(String name) {
        this(name, DEFAULT_EXECUTOR);
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
    public Channel find(Integer id) {
        Channel c = nonServerChannels.get(id);
        if (c != null) {
            return c;
        } else {
            return serverChannels.get(id);
        }
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Integer) {
            return nonServerChannels.containsKey(o) || serverChannels.containsKey(o);
        } else if (o instanceof Channel) {
            Channel c = (Channel) o;
            if (o instanceof ServerChannel) {
                return serverChannels.containsKey(c.id());
            } else {
                return nonServerChannels.containsKey(c.id());
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean add(Channel channel) {
        ConcurrentMap<Integer, Channel> map =
            channel instanceof ServerChannel? serverChannels : nonServerChannels;

        boolean added = map.putIfAbsent(channel.id(), channel) == null;
        if (added) {
            channel.closeFuture().addListener(remover);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        Channel c = null;
        if (o instanceof Integer) {
            c = nonServerChannels.remove(o);
            if (c == null) {
                c = serverChannels.remove(o);
            }
        } else if (o instanceof Channel) {
            c = (Channel) o;
            if (c instanceof ServerChannel) {
                c = serverChannels.remove(c.id());
            } else {
                c = nonServerChannels.remove(c.id());
            }
        }

        if (c == null) {
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
                serverChannels.values().iterator(),
                nonServerChannels.values().iterator());
    }

    @Override
    public Object[] toArray() {
        Collection<Channel> channels = new ArrayList<Channel>(size());
        channels.addAll(serverChannels.values());
        channels.addAll(nonServerChannels.values());
        return channels.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Collection<Channel> channels = new ArrayList<Channel>(size());
        channels.addAll(serverChannels.values());
        channels.addAll(nonServerChannels.values());
        return channels.toArray(a);
    }

    @Override
    public ChannelGroupFuture close() {
        Map<Integer, ChannelFuture> futures =
            new LinkedHashMap<Integer, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.id(), c.close().awaitUninterruptibly());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.id(), c.close());
        }

        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroupFuture disconnect() {
        Map<Integer, ChannelFuture> futures =
            new LinkedHashMap<Integer, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.id(), c.disconnect());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.id(), c.disconnect());
        }

        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroupFuture write(Object message) {
        if (message == null) {
            throw new NullPointerException("message");
        }

        Map<Integer, ChannelFuture> futures = new LinkedHashMap<Integer, ChannelFuture>(size());
        for (Channel c: nonServerChannels.values()) {
            BufUtil.retain(message);
            futures.put(c.id(), c.write(message));
        }

        BufUtil.release(message);
        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroupFuture sendFile(FileRegion region) {
        if (region == null) {
            throw new NullPointerException("region");
        }

        Map<Integer, ChannelFuture> futures = new LinkedHashMap<Integer, ChannelFuture>(size());
        for (Channel c: nonServerChannels.values()) {
            BufUtil.retain(region);
            futures.put(c.id(), c.sendFile(region));
        }

        BufUtil.release(region);
        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroupFuture flush() {
        Map<Integer, ChannelFuture> futures = new LinkedHashMap<Integer, ChannelFuture>(size());
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.id(), c.flush());
        }

        return new DefaultChannelGroupFuture(this, futures, executor);
    }

    @Override
    public ChannelGroupFuture deregister() {
        Map<Integer, ChannelFuture> futures =
                new LinkedHashMap<Integer, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.id(), c.deregister());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.id(), c.deregister());
        }

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
        return getClass().getSimpleName() +
               "(name: " + name() + ", size: " + size() + ')';
    }
}
