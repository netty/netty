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
package org.jboss.netty.channel.group;

import java.net.SocketAddress;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.util.CombinedIterator;
import org.jboss.netty.util.ConcurrentHashMap;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class DefaultChannelGroup extends AbstractSet<Channel> implements ChannelGroup {

    private final String name;
    private final ConcurrentMap<UUID, Channel> serverChannels = new ConcurrentHashMap<UUID, Channel>();
    private final ConcurrentMap<UUID, Channel> nonServerChannels = new ConcurrentHashMap<UUID, Channel>();
    private final ChannelFutureListener remover = new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
            remove(future.getChannel());
        }
    };

    public DefaultChannelGroup(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
    }

    public String getName() {
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

    public Channel find(UUID id) {
        Channel c = nonServerChannels.get(id);
        if (c != null) {
            return c;
        } else {
            return serverChannels.get(id);
        }
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof UUID) {
            return nonServerChannels.containsKey(o) || serverChannels.containsKey(o);
        } else if (o instanceof Channel) {
            Channel c = (Channel) o;
            if (o instanceof ServerChannel) {
                return serverChannels.containsKey(c.getId());
            } else {
                return nonServerChannels.containsKey(c.getId());
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean add(Channel channel) {
        ConcurrentMap<UUID, Channel> map =
            channel instanceof ServerChannel? serverChannels : nonServerChannels;

        boolean added = map.putIfAbsent(channel.getId(), channel) == null;
        if (added) {
            channel.getCloseFuture().addListener(remover);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        Channel c = null;
        if (o instanceof UUID) {
            c = nonServerChannels.remove(o);
            if (c == null) {
                c = serverChannels.remove(o);
            }
        } else if (o instanceof Channel) {
            c = (Channel) o;
            if (c instanceof ServerChannel) {
                c = serverChannels.remove(c.getId());
            } else {
                c = nonServerChannels.remove(c.getId());
            }
        }

        if (c == null) {
            return false;
        }

        c.getCloseFuture().removeListener(remover);
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

    public ChannelGroupFuture close() {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.getId(), c.close().awaitUninterruptibly());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.getId(), c.close());
        }

        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture disconnect() {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.getId(), c.disconnect().awaitUninterruptibly());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.getId(), c.disconnect());
        }

        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture setInterestOps(int interestOps) {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.getId(), c.setInterestOps(interestOps).awaitUninterruptibly());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.getId(), c.setInterestOps(interestOps));
        }

        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture setReadable(boolean readable) {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.getId(), c.setReadable(readable).awaitUninterruptibly());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.getId(), c.setReadable(readable));
        }

        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture unbind() {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());

        for (Channel c: serverChannels.values()) {
            futures.put(c.getId(), c.unbind().awaitUninterruptibly());
        }
        for (Channel c: nonServerChannels.values()) {
            futures.put(c.getId(), c.unbind());
        }

        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture write(Object message) {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());
        for (Channel c: this) {
            futures.put(c.getId(), c.write(message));
        }
        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture write(Object message, SocketAddress remoteAddress) {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());
        for (Channel c: this) {
            futures.put(c.getId(), c.write(message, remoteAddress));
        }
        return new DefaultChannelGroupFuture(this, futures);
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChannelGroup)) {
            return false;
        }

        ChannelGroup that = (ChannelGroup) o;
        return getName().equals(that.getName());
    }

    public int compareTo(ChannelGroup o) {
        return getName().compareTo(o.getName());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + name + ')';
    }
}
