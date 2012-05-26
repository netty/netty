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

import static com.sun.nio.sctp.SctpStandardSocketOptions.*;
import io.netty.channel.ChannelConfig;

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
 * <td>{@code "sctpNoDelay"}</td><td>{@link #setSctpNoDelay(boolean)}}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSize"}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "sendBufferSize"}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "sctpInitMaxStreams"}</td><td>{@link #setInitMaxStreams(InitMaxStreams)}</td>
 * </tr>
 * </table>
 */
public interface SctpChannelConfig extends ChannelConfig {

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_NODELAY}</a> option.
     */
    boolean isSctpNoDelay();

    /**
     * Sets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_NODELAY}</a> option.
     */
    void setSctpNoDelay(boolean sctpNoDelay);

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_SNDBUF}</a> option.
     */
    int getSendBufferSize();

    /**
     * Sets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_SNDBUF}</a> option.
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_RCVBUF}</a> option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SO_RCVBUF}</a> option.
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    InitMaxStreams getInitMaxStreams();

    /**
     * Gets the <a href="http://openjdk.java.net/projects/sctp/javadoc/com/sun/nio/sctp/SctpStandardSocketOption.html">
     *     {@code SCTP_INIT_MAXSTREAMS}</a> option.
     */
    void setInitMaxStreams(InitMaxStreams initMaxStreams);
}
