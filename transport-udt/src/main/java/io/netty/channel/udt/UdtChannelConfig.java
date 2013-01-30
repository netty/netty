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
package io.netty.channel.udt;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;

import com.barchart.udt.OptionUDT;
import com.barchart.udt.TypeUDT;
import com.barchart.udt.nio.KindUDT;

/**
 * A {@link ChannelConfig} for a {@link UdtChannel}.
 * <p>
 * <h3>Available options</h3>
 * In addition to the options provided by {@link ChannelConfig},
 * {@link UdtChannelConfig} allows the following options in the option map:
 * <p>
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * <th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link io.netty.channel.ChannelOption#SO_REUSEADDR}</td><td>{@link #setReuseAddress(boolean)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.ChannelOption#SO_RCVBUF}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.ChannelOption#SO_SNDBUF}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.ChannelOption#SO_REUSEADDR}</td><td>{@link #setReuseAddress(boolean)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.ChannelOption#SO_LINGER}</td><td>{@link #setSoLinger(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.ChannelOption#SO_RCVBUF}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.ChannelOption#SO_SNDBUF}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.udt.UdtChannelOption#PROTOCOL_RECEIVE_BUFFER_SIZE}</td>
 * <td>{@link #setProtocolReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.udt.UdtChannelOption#PROTOCOL_SEND_BUFFER_SIZE}</td>
 * <td>{@link #setProtocolSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.udt.UdtChannelOption#SYSTEM_RECEIVE_BUFFER_SIZE}</td>
 * <td>{@link #setSystemReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link io.netty.channel.udt.UdtChannelOption#SYSTEM_SEND_BUFFER_SIZE}</td>
 * <td>{@link #setSystemSendBufferSize(int)}</td>

 * </tr>
 * </table>
 * <p>
 * Note that {@link TypeUDT#DATAGRAM} message oriented channels treat
 * {@code "receiveBufferSize"} and {@code "sendBufferSize"} as maximum message
 * size. If received or sent message does not fit specified sizes,
 * {@link ChannelException} will be thrown.
 */
public interface UdtChannelConfig extends ChannelConfig {

    /**
     * Gets {@link OptionUDT#Protocol_Receive_Buffer_Size}
     */
    int getProtocolReceiveBufferSize();

    /**
     * Gets {@link OptionUDT#Protocol_Send_Buffer_Size}
     */
    int getProtocolSendBufferSize();

    /**
     * Gets the {@link ChannelOption#SO_RCVBUF} option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the {@link ChannelOption#SO_SNDBUF} option.
     */
    int getSendBufferSize();

    /**
     * Gets the {@link ChannelOption#SO_LINGER} option.
     */
    int getSoLinger();

    /**
     * Gets {@link OptionUDT#System_Receive_Buffer_Size}
     */
    int getSystemReceiveBufferSize();

    /**
     * Gets {@link OptionUDT#System_Send_Buffer_Size}
     */
    int getSystemSendBufferSize();

    /**
     * Gets the {@link ChannelOption#SO_REUSEADDR} option.
     */
    boolean isReuseAddress();

    /**
     * Sets {@link OptionUDT#Protocol_Receive_Buffer_Size}
     */
    UdtChannelConfig setProtocolReceiveBufferSize(int size);

    /**
     * Sets {@link OptionUDT#Protocol_Send_Buffer_Size}
     */
    UdtChannelConfig setProtocolSendBufferSize(int size);

    /**
     * Sets the {@link ChannelOption#SO_RCVBUF} option.
     */
    UdtChannelConfig setReceiveBufferSize(int receiveBufferSize);

    /**
     * Sets the {@link ChannelOption#SO_REUSEADDR} option.
     */
    UdtChannelConfig setReuseAddress(boolean reuseAddress);

    /**
     * Sets the {@link ChannelOption#SO_SNDBUF} option.
     */
    UdtChannelConfig setSendBufferSize(int sendBufferSize);

    /**
     * Sets the {@link ChannelOption#SO_LINGER} option.
     */
    UdtChannelConfig setSoLinger(int soLinger);

    /**
     * Sets {@link OptionUDT#System_Receive_Buffer_Size}
     */
    UdtChannelConfig setSystemReceiveBufferSize(int size);

    /**
     * Sets {@link OptionUDT#System_Send_Buffer_Size}
     */
    UdtChannelConfig setSystemSendBufferSize(int size);

}
