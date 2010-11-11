/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;

import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictor;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;
import org.jboss.netty.channel.ReceiveBufferSizePredictorFactory;

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
 * <td>{@code "receiveBufferSizePredictorFactory"}</td><td>{@link #setReceiveBufferSizePredictorFactory(ReceiveBufferSizePredictorFactory)}</td>
 * </tr><tr>
 * <td>{@code "sendBufferSize"}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "timeToLive"}</td><td>{@link #setTimeToLive(int)}</td>
 * </tr><tr>
 * <td>{@code "trafficClass"}</td><td>{@link #setTrafficClass(int)}</td>
 * </tr>
 * </table>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_BROADCAST}</a> option.
     */
    boolean isBroadcast();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_BROADCAST}</a> option.
     */
    void setBroadcast(boolean broadcast);

    /**
     * Gets the setting for local loopback of multicast datagrams.
     *
     * @return {@code true} if and only if the loopback mode has been disabled
     */
    boolean isLoopbackModeDisabled();

    /**
     * Sets the setting for local loopback of multicast datagrams.
     *
     * @param loopbackModeDisabled
     *        {@code true} if and only if the loopback mode has been disabled
     */
    void setLoopbackModeDisabled(boolean loopbackModeDisabled);

    /**
     * Gets the default time-to-live for multicast packets sent out on the
     * socket.
     */
    int getTimeToLive();

    /**
     * Sets the default time-to-live for multicast packets sent out on the
     * {@link DatagramChannel} in order to control the scope of the multicasts.
     */
    void setTimeToLive(int ttl);

    /**
     * Gets the address of the network interface used for multicast packets.
     */
    InetAddress getInterface();

    /**
     * Sets the address of the network interface used for multicast packets.
     */
    void setInterface(InetAddress interfaceAddress);

    /**
     * Gets the network interface for outgoing multicast datagrams sent on
     * the {@link DatagramChannel}.
     */
    NetworkInterface getNetworkInterface();

    /**
     * Sets the network interface for outgoing multicast datagrams sent on
     * the {@link DatagramChannel}.
     */
    void setNetworkInterface(NetworkInterface networkInterface);

    /**
     * Returns the {@link ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link FixedReceiveBufferSizePredictor}(768)</tt>.
     */
    ReceiveBufferSizePredictor getReceiveBufferSizePredictor();

    /**
     * Sets the {@link ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link FixedReceiveBufferSizePredictor}(768)</tt>.
     */
    void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);

    /**
     * Returns the {@link ReceiveBufferSizePredictorFactory} which creates a new
     * {@link ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link FixedReceiveBufferSizePredictorFactory}(768)</tt>.
     */
    ReceiveBufferSizePredictorFactory getReceiveBufferSizePredictorFactory();

    /**
     * Sets the {@link ReceiveBufferSizePredictor} which creates a new
     * {@link ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link FixedReceiveBufferSizePredictorFactory}(768)</tt>.
     */
    void setReceiveBufferSizePredictorFactory(ReceiveBufferSizePredictorFactory predictorFactory);
}
