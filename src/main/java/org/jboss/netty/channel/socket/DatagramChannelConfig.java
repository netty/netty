/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.channel.socket;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.StandardSocketOptions;

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
 * <td>{@code "receiveBufferSizePredictor"}</td>
 * <td>{@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSizePredictorFactory"}</td>
 * <td>{@link #setReceiveBufferSizePredictorFactory(ReceiveBufferSizePredictorFactory)}</td>
 * </tr><tr>
 * <td>{@code "sendBufferSize"}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "timeToLive"}</td><td>{@link #setTimeToLive(int)}</td>
 * </tr><tr>
 * <td>{@code "trafficClass"}</td><td>{@link #setTrafficClass(int)}</td>
 * </tr>
 * </table>
 */
public interface DatagramChannelConfig extends ChannelConfig {

    /**
     * Gets the {@link StandardSocketOptions#SO_SNDBUF} option.
     */
    int getSendBufferSize();

    /**
     * Sets the {@link StandardSocketOptions#SO_SNDBUF} option.
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Gets the {@link StandardSocketOptions#SO_RCVBUF} option.
     */
    int getReceiveBufferSize();

    /**
     * Sets the {@link StandardSocketOptions#SO_RCVBUF} option.
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Gets the {@link StandardSocketOptions#IP_TOS} option.
     */
    int getTrafficClass();

    /**
     * Gets the {@link StandardSocketOptions#IP_TOS} option.
     */
    void setTrafficClass(int trafficClass);

    /**
     * Gets the {@link StandardSocketOptions#SO_REUSEADDR} option.
     */
    boolean isReuseAddress();

    /**
     * Sets the {@link StandardSocketOptions#SO_REUSEADDR} option.
     */
    void setReuseAddress(boolean reuseAddress);

    /**
     * Gets the {@link StandardSocketOptions#SO_BROADCAST} option.
     */
    boolean isBroadcast();

    /**
     * Sets the {@link StandardSocketOptions#SO_BROADCAST} option.
     */
    void setBroadcast(boolean broadcast);

    /**
     * Gets the {@link StandardSocketOptions#IP_MULTICAST_LOOP} option.
     */
    boolean isLoopbackModeDisabled();

    /**
     * Sets the {@link StandardSocketOptions#IP_MULTICAST_LOOP} option.
     *
     * @param loopbackModeDisabled
     *        {@code true} if and only if the loopback mode has been disabled
     */
    void setLoopbackModeDisabled(boolean loopbackModeDisabled);

    /**
     * Gets the {@link StandardSocketOptions#IP_MULTICAST_TTL} option.
     */
    int getTimeToLive();

    /**
     * Sets the {@link StandardSocketOptions#IP_MULTICAST_TTL} option.
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
     * Gets the {@link StandardSocketOptions#IP_MULTICAST_IF} option.
     */
    NetworkInterface getNetworkInterface();

    /**
     * Sets the {@link StandardSocketOptions#IP_MULTICAST_IF} option.
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
