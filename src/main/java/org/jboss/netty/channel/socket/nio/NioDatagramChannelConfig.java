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
package org.jboss.netty.channel.socket.nio;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelConfig;

/**
 * A {@link DatagramChannelConfig} for a NIO TCP/IP {@link DatagramChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig} and
 * {@link DatagramChannelConfig}, {@link NioDatagramChannelConfig} allows the
 * following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "writeBufferHighWaterMark"}</td><td>{@link #setWriteBufferHighWaterMark(int)}</td>
 * </tr><tr>
 * <td>{@code "writeBufferLowWaterMark"}</td><td>{@link #setWriteBufferLowWaterMark(int)}</td>
 * </tr><tr>
 * <td>{@code "writeSpinCount"}</td><td>{@link #setWriteSpinCount(int)}</td>
 * </tr><tr>
 * </table>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface NioDatagramChannelConfig extends DatagramChannelConfig {

    /**
     * Returns the high water mark of the write buffer.  If the number of bytes
     * queued in the write buffer exceeds this value, {@link Channel#isWritable()}
     * will start to return {@code true}.
     */
    int getWriteBufferHighWaterMark();

    /**
     * Sets the high water mark of the write buffer.  If the number of bytes
     * queued in the write buffer exceeds this value, {@link Channel#isWritable()}
     * will start to return {@code true}.
     */
    void setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    /**
     * Returns the low water mark of the write buffer.  Once the number of bytes
     * queued in the write buffer exceeded the
     * {@linkplain #setWriteBufferHighWaterMark(int) high water mark} and then
     * dropped down below this value, {@link Channel#isWritable()} will return
     * {@code false} again.
     */
    int getWriteBufferLowWaterMark();

    /**
     * Sets the low water mark of the write buffer.  Once the number of bytes
     * queued in the write buffer exceeded the
     * {@linkplain #setWriteBufferHighWaterMark(int) high water mark} and then
     * dropped down below this value, {@link Channel#isWritable()} will return
     * {@code false} again.
     */
    void setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

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
}
