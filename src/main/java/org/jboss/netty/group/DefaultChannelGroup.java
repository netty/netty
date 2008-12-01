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
package org.jboss.netty.group;

import java.net.SocketAddress;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class DefaultChannelGroup extends AbstractSet<Channel> implements ChannelGroup {

    private final String name;
    private final ConcurrentMap<UUID, Channel> channels = new ConcurrentHashMap<UUID, Channel>();
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
        return channels.isEmpty();
    }

    @Override
    public int size() {
        return channels.size();
    }

    public Channel find(UUID id) {
        return channels.get(id);
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof UUID) {
            return channels.containsKey(o);
        } else if (o instanceof Channel) {
            return channels.containsKey(((Channel) o).getId());
        } else {
            return false;
        }
    }

    @Override
    public boolean add(Channel channel) {
        boolean added = channels.putIfAbsent(channel.getId(), channel) == null;
        if (added) {
            channel.getCloseFuture().addListener(remover);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        Channel c = null;
        if (o instanceof UUID) {
            c = channels.remove(o);
        } else if (o instanceof Channel) {
            c = channels.remove(((Channel) o).getId());
        }

        if (c == null) {
            return false;
        }

        c.getCloseFuture().removeListener(remover);
        return true;
    }

    @Override
    public void clear() {
        channels.clear();
    }

    @Override
    public Iterator<Channel> iterator() {
        return channels.values().iterator();
    }

    @Override
    public Object[] toArray() {
        return channels.values().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return channels.values().toArray(a);
    }

    public ChannelGroupFuture close() {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());
        for (Channel c: this) {
            futures.put(c.getId(), c.close());
        }
        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture disconnect() {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());
        for (Channel c: this) {
            futures.put(c.getId(), c.disconnect());
        }
        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture setInterestOps(int interestOps) {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());
        for (Channel c: this) {
            futures.put(c.getId(), c.setInterestOps(interestOps));
        }
        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture setReadable(boolean readable) {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());
        for (Channel c: this) {
            futures.put(c.getId(), c.setReadable(readable));
        }
        return new DefaultChannelGroupFuture(this, futures);
    }

    public ChannelGroupFuture unbind() {
        Map<UUID, ChannelFuture> futures =
            new HashMap<UUID, ChannelFuture>(size());
        for (Channel c: this) {
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
