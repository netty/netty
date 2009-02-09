/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.channel.local;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.util.ConcurrentWeakHashMap;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 * @author Trustin Lee (tlee@redhat.com)
 */
public final class LocalAddress extends SocketAddress implements Comparable<LocalAddress> {
    private static final long serialVersionUID = -3601961747680808645L;

    private static final ConcurrentMap<String, LocalAddress> addresses =
        new ConcurrentWeakHashMap<String, LocalAddress>();

    private static final AtomicInteger nextEphemeralPort = new AtomicInteger();

    public static LocalAddress getInstance(String id) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        LocalAddress a = addresses.get(id);
        if (a == null) {
            a = new LocalAddress(id);
            LocalAddress oldA = addresses.putIfAbsent(id, a);
            if (oldA != null) {
                a = oldA;
            }
        }

        return a;
    }

    public static LocalAddress newEphemeralInstance() {
        for (long i = (long) Integer.MAX_VALUE - Integer.MIN_VALUE; i >= 0; i --) {
            String id = "ephemeral-" +
                        Integer.toHexString(nextEphemeralPort.incrementAndGet());
            LocalAddress a = new LocalAddress(id);
            if (addresses.putIfAbsent(id, a) == null) {
                return a;
            }
        }

        // Not likely to reach here but let's be a paranoid.
        throw new ChannelException("failed to allocate a local ephemeral port");
    }

    private final String id;
    private final boolean ephemeral;

    private LocalAddress(String id) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        this.id = id;

        ephemeral = id.startsWith("ephemeral-");
    }

    public String getId() {
        return id;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocalAddress)) {
            return false;
        }

        return getId().equals(((LocalAddress) o).getId());
    }

    public int compareTo(LocalAddress o) {
        return getId().compareTo(o.getId());
    }

    @Override
    public String toString() {
        return getId();
    }

    // Just in case someone serializes this class ..
    private Object readResolve() {
        return getInstance(getId());
    }
}
