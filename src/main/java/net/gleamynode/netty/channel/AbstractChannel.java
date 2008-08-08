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

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.gleamynode.netty.util.TimeBasedUuidGenerator;

/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public abstract class AbstractChannel implements Channel, Comparable<Channel> {

    private final UUID id = TimeBasedUuidGenerator.generate();
    private final Channel parent;
    private final ChannelFactory factory;
    private final ChannelPipeline pipeline;
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this);

    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile int interestOps = OP_READ;

    protected AbstractChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline) {

        this.parent = parent;
        this.factory = factory;
        this.pipeline = pipeline;
    }

    public final UUID getId() {
        return id;
    }

    public Channel getParent() {
        return parent;
    }

    public ChannelFactory getFactory() {
        return factory;
    }

    public ChannelPipeline getPipeline() {
        return pipeline;
    }

    protected ChannelFuture getSucceededFuture() {
        return succeededFuture;
    }

    protected ChannelFuture getUnsupportedOperationFuture() {
        return new FailedChannelFuture(this, new UnsupportedOperationException());
    }

    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public final boolean equals(Object o) {
        return this == o;
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

    public ChannelFuture bind(SocketAddress localAddress) {
        return ChannelUtil.bind(this, localAddress);
    }

    public ChannelFuture close() {
        return ChannelUtil.close(this);
    }

    public ChannelFuture connect(SocketAddress remoteAddress) {
        return ChannelUtil.connect(this, remoteAddress);
    }

    public ChannelFuture disconnect() {
        return ChannelUtil.disconnect(this);
    }

    public int getInterestOps() {
        return interestOps;
    }

    public ChannelFuture setInterestOps(int interestOps) {
        return ChannelUtil.setInterestOps(this, interestOps);
    }

    protected void setInterestOpsNow(int interestOps) {
        this.interestOps = interestOps;
    }

    public boolean isReadable() {
        return (getInterestOps() & OP_READ) != 0;
    }

    public boolean isWritable() {
        return (getInterestOps() & OP_WRITE) == 0;
    }

    public ChannelFuture setReadable(boolean readable) {
        if (readable) {
            return setInterestOps(getInterestOps() | OP_READ);
        } else {
            return setInterestOps(getInterestOps() & ~OP_READ);
        }
    }

    public ChannelFuture write(Object message) {
        return ChannelUtil.write(this, message);
    }

    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        return ChannelUtil.write(this, message, remoteAddress);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(128);
        buf.append(getClass().getSimpleName());
        buf.append("(id: ");
        buf.append(id.toString());

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
