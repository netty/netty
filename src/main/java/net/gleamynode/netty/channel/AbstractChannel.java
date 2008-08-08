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
package net.gleamynode.netty.channel;

import java.util.concurrent.atomic.AtomicBoolean;


public abstract class AbstractChannel implements Channel, Comparable<Channel> {
    private final Long id = Long.valueOf(hashCode() & 0xFFFFFFFFL);
    private final AtomicBoolean closed = new AtomicBoolean();

    public final Long getId() {
        return id;
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    public int compareTo(Channel o) {
        return System.identityHashCode(this) - System.identityHashCode(o);
    }

    public boolean isOpen() {
        return !closed.get();
    }

    protected boolean setClosed() {
        return closed.compareAndSet(false, true);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(128);
        buf.append(getClass().getSimpleName());
        buf.append("(id: ");
        buf.append(id.longValue());

        if (isConnected()) {
            buf.append(", ");
            if (getParent() == null) {
                buf.append(getLocalAddress());
                buf.append(" => ");
                buf.append(getRemoteAddress());
            } else {
                buf.append(getRemoteAddress());
                buf.append(" => ");
                buf.append(getLocalAddress());
            }
        } else if (isBound()) {
            buf.append(", ");
            buf.append(getLocalAddress());
        }

        buf.append(')');

        return buf.toString();
    }
}
