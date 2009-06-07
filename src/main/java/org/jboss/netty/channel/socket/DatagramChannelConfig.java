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
package org.jboss.netty.channel.socket;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;

/**
 * A {@link ChannelConfig} for a {@link DatagramChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig},
 * {@link DatagramChannelConfig} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "broadcast"}</td><td>{@link #setBroadcast(boolean)}</td>
 * </tr><tr>
 * <td>{@code "interface"}</td><td>{@link #setInterface(InetAddress)}</td>
 * </tr><tr>
 * <td>{@code "loopbackModeDisabled"}</td><td>{@link #setLoopbackModeDisabled(boolean)}</td>
 * </tr><tr>
 * <td>{@code "networkInterface"}</td><td>{@link #setNetworkInterface(NetworkInterface)}</td>
 * </tr><tr>
 * <td>{@code "reuseAddress"}</td><td>{@link #setReuseAddress(boolean)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSize"}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSizePredictor"}</td><td>{@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}</td>
 * </tr><tr>
 * <td>{@code "sendBufferSize"}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "timeToLive"}</td><td>{@link #setTimeToLive(int)}</td>
 * </tr><tr>
 * <td>{@code "trafficClass"}</td><td>{@link #setTrafficClass(int)}</td>
 * </tr>
 * </table>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public interface DatagramChannelConfig extends ChannelConfig {

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_SNDBUF}</a> option.
     */
    int getSendBufferSize();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_SNDBUF}</a> option.
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_RCVBUF}</a> option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_RCVBUF}</a> option.
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Gets the traffic class.
     */
    int getTrafficClass();

    /**
     * Sets the traffic class as specified in {@link DatagramSocket#setTrafficClass(int)}.
     */
    void setTrafficClass(int trafficClass);

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_REUSEADDR}</a> option.
     */
    boolean isReuseAddress();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_REUSEADDR}</a> option.
     */
    void setReuseAddress(boolean reuseAddress);

    boolean isBroadcast();

    void setBroadcast(boolean broadcast);

    boolean isLoopbackModeDisabled();

    void setLoopbackModeDisabled(boolean loopbackModeDisabled);

    int getTimeToLive();

    void setTimeToLive(int ttl);

    InetAddress getInterface();
    void setInterface(InetAddress interfaceAddress);

    NetworkInterface getNetworkInterface();
    void setNetworkInterface(NetworkInterface networkInterface);

    ReceiveBufferSizePredictor getReceiveBufferSizePredictor();
    void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);
    
    /**
     * Returns the maximum loop count for a write operation until
     * {@link WritableByteChannel#write(ByteBuffer)} returns a non-zero value.
     * It is similar to what a spin lock is used for in concurrency programming.
     * It improves memory utilization and write throughput depending on
     * the platform that JVM runs on.  The default value is {@code 16}.
     */
    int getWriteSpinCount();

    /**
     * Sets the maximum loop count for a write operation until
     * {@link WritableByteChannel#write(ByteBuffer)} returns a non-zero value.
     * It is similar to what a spin lock is used for in concurrency programming.
     * It improves memory utilization and write throughput depending on
     * the platform that JVM runs on.  The default value is {@code 16}.
     *
     * @throws IllegalArgumentException
     *         if the specified value is {@code 0} or less than {@code 0}
     */
    void setWriteSpinCount(int writeSpinCount);
    
    int getWriteBufferHighWaterMark();
    void setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    int getWriteBufferLowWaterMark();
    void setWriteBufferLowWaterMark(int writeBufferLowWaterMark);
}
