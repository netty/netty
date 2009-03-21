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

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.util.ConcurrentHashMap;

/**
 * A skeletal {@link Channel} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public abstract class AbstractChannel implements Channel {

    static final ConcurrentMap<Integer, Channel> allChannels = new ConcurrentHashMap<Integer, Channel>();
    private static final IdDeallocator ID_DEALLOCATOR = new IdDeallocator();

    private static Integer allocateId(Channel channel) {
        Integer id = Integer.valueOf(System.identityHashCode(channel));
        for (;;) {
            // Loop until a unique ID is acquired.
            // It should be found in one loop practically.
            if (allChannels.putIfAbsent(id, channel) == null) {
                // Successfully acquired.
                return id;
            } else {
                // Taken by other channel at almost the same moment.
                id = Integer.valueOf(id.intValue() + 1);
            }
        }
    }

    private static final class IdDeallocator implements ChannelFutureListener {
        IdDeallocator() {
            super();
        }

        public void operationComplete(ChannelFuture future) throws Exception {
            allChannels.remove(future.getChannel().getId());
        }
    }

    private final Integer id = allocateId(this);
    private final Channel parent;
    private final ChannelFactory factory;
    private final ChannelPipeline pipeline;
    private final ChannelFuture succeededFuture = new SucceededChannelFuture(this);
    private final ChannelFuture closeFuture = new UnfailingChannelFuture(this, false);
    private volatile int interestOps = OP_READ;

    /** Cache for the string representation of this channel */
    private String strVal;

    /**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     * @param factory
     *        the factory which created this channel
     * @param pipeline
     *        the pipeline which is going to be attached to this channel
     * @param sink
     *        the sink which will receive downstream events from the pipeline
     *        and send upstream events to the pipeline
     */
    protected AbstractChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink) {

        this.parent = parent;
        this.factory = factory;
        this.pipeline = pipeline;
        closeFuture.addListener(ID_DEALLOCATOR);
        pipeline.attach(this, sink);
    }

    public final Integer getId() {
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

    /**
     * Returns the cached {@link SucceededChannelFuture} instance.
     */
    protected ChannelFuture getSucceededFuture() {
        return succeededFuture;
    }

    /**
     * Returns the {@link FailedChannelFuture} whose cause is an
     * {@link UnsupportedOperationException}.
     */
    protected ChannelFuture getUnsupportedOperationFuture() {
        return new FailedChannelFuture(this, new UnsupportedOperationException());
    }

    /**
     * Returns the {@linkplain System#identityHashCode(Object) identity hash code}
     * of this channel.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Returns {@code true} if and only if the specified object is identical
     * with this channel (i.e: {@code this == o}).
     */
    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    /**
     * Compares the {@linkplain #getId() ID} of the two channels.
     */
    public final int compareTo(Channel o) {
        return getId().compareTo(o.getId());
    }

    public boolean isOpen() {
        return !closeFuture.isDone();
    }

    /**
     * Marks this channel as closed.  This method is intended to be called by
     * an internal component - please do not call it unless you know what you
     * are doing.
     *
     * @return {@code true} if and only if this channel was not marked as
     *                      closed yet
     */
    protected boolean setClosed() {
        return closeFuture.setSuccess();
    }

    public ChannelFuture bind(SocketAddress localAddress) {
        return Channels.bind(this, localAddress);
    }

    public ChannelFuture unbind() {
        return Channels.unbind(this);
    }

    public ChannelFuture close() {
        ChannelFuture returnedCloseFuture = Channels.close(this);
        assert closeFuture == returnedCloseFuture;
        return closeFuture;
    }

    public ChannelFuture getCloseFuture() {
        return closeFuture;
    }

    public ChannelFuture connect(SocketAddress remoteAddress) {
        return Channels.connect(this, remoteAddress);
    }

    public ChannelFuture disconnect() {
        return Channels.disconnect(this);
    }

    public int getInterestOps() {
        return interestOps;
    }

    public ChannelFuture setInterestOps(int interestOps) {
        return Channels.setInterestOps(this, interestOps);
    }

    /**
     * Sets the {@link #getInterestOps() interestOps} property of this channel
     * immediately.  This method is intended to be called by an internal
     * component - please do not call it unless you know what you are doing.
     */
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
        return Channels.write(this, message);
    }

    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        return Channels.write(this, message, remoteAddress);
    }

    /**
     * Returns the {@link String} representation of this channel.  The returned
     * string contains the {@linkplain #getId() ID}, {@linkplain #getLocalAddress() local address},
     * and {@linkplain #getRemoteAddress() remote address} of this channel for
     * easier identification.
     */
    @Override
    public String toString() {
        boolean connected = isConnected();
        if (connected && strVal != null) {
            return strVal;
        }

        StringBuilder buf = new StringBuilder(128);
        buf.append("[id: 0x");
        buf.append(getIdString());

        if (connected) {
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

        buf.append(']');

        String strVal = buf.toString();
        if (connected) {
            this.strVal = strVal;
        } else {
            this.strVal = null;
        }
        return strVal;
    }

    private String getIdString() {
        String answer = Integer.toHexString(id.intValue());
        switch (answer.length()) {
        case 0:
            answer = "00000000";
            break;
        case 1:
            answer = "0000000" + answer;
            break;
        case 2:
            answer = "000000" + answer;
            break;
        case 3:
            answer = "00000" + answer;
            break;
        case 4:
            answer = "0000" + answer;
            break;
        case 5:
            answer = "000" + answer;
            break;
        case 6:
            answer = "00" + answer;
            break;
        case 7:
            answer = "0" + answer;
            break;
        }
        return answer;
    }
}
