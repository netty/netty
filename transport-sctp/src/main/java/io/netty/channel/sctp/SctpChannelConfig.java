/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import com.sun.nio.sctp.SctpStandardSocketOptions.InitMaxStreams;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

/**
 * A {@link ChannelConfig} for a {@link SctpChannel}.
 * <p/>
 * <h3>Available options</h3>
 * <p/>
 * In addition to the options provided by {@link ChannelConfig},
 * {@link SctpChannelConfig} allows the following options in the option map:
 * <p/>
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_RCVBUF}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_SNDBUF}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@link SctpChannelOption#SCTP_NODELAY}</td><td>{@link #setSctpNoDelay(boolean)}}</td>
 * </tr><tr>
 * <td>{@link SctpChannelOption#SCTP_INIT_MAXSTREAMS}</td><td>{@link #setInitMaxStreams(InitMaxStreams)}</td>
 * </tr>
 * </table>
 */
public interface SctpChannelConfig extends ChannelConfig {

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     * {@code SCTP_NODELAY}</a> option.  Please note that the default value of this option is {@code true} unlike the
     * operating system default ({@code false}). However, for some buggy platforms, such as Android, that shows erratic
     * behavior with Nagle's algorithm disabled, the default value remains to be {@code false}.
     */
    boolean isSctpNoDelay();

    /**
     * Sets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     * {@code SCTP_NODELAY}</a> option.  Please note that the default value of this option is {@code true} unlike the
     * operating system default ({@code false}). However, for some buggy platforms, such as Android, that shows erratic
     * behavior with Nagle's algorithm disabled, the default value remains to be {@code false}.
     */
    SctpChannelConfig setSctpNoDelay(boolean sctpNoDelay);

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_SNDBUF}</a> option.
     */
    int getSendBufferSize();

    /**
     * Sets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_SNDBUF}</a> option.
     */
    SctpChannelConfig setSendBufferSize(int sendBufferSize);

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_RCVBUF}</a> option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_RCVBUF}</a> option.
     */
    SctpChannelConfig setReceiveBufferSize(int receiveBufferSize);

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    InitMaxStreams getInitMaxStreams();

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    SctpChannelConfig setInitMaxStreams(InitMaxStreams initMaxStreams);

    @Override
    SctpChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    @Deprecated
    SctpChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    SctpChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    SctpChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    SctpChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    SctpChannelConfig setAutoRead(boolean autoRead);

    @Override
    SctpChannelConfig setAutoClose(boolean autoClose);

    @Override
    SctpChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    SctpChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    SctpChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

    @Override
    SctpChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);
}
