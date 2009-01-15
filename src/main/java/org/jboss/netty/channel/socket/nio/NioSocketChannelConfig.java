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
package org.jboss.netty.channel.socket.nio;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.SocketChannelConfig;

/**
 * A {@link SocketChannelConfig} for a NIO TCP/IP {@link SocketChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig} and
 * {@link SocketChannelConfig}, {@link NioSocketChannelConfig} allows the
 * following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "writeSpinCount"}</td><td>{@link #setWriteSpinCount(int)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSizePredictor"}</td><td>{@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}</td>
 * </tr><tr>
 * <td>{@code "readWriteFair"}</td><td>{@link #setReadWriteFair(boolean)}</td>
 * </tr>
 * </table>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has org.jboss.netty.channel.socket.nio.ReceiveBufferSizePredictor
 */
public interface NioSocketChannelConfig extends SocketChannelConfig {

    int getWriteBufferHighWaterMark();
    void setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    int getWriteBufferLowWaterMark();
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

    /**
     * Returns the {@link ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is {@link DefaultReceiveBufferSizePredictor}.
     */
    ReceiveBufferSizePredictor getReceiveBufferSizePredictor();

    /**
     * Sets the {@link ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is {@link DefaultReceiveBufferSizePredictor}.
     */
    void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);

    /**
     * @deprecated This property has been replaced by the
     * {@code writeBufferHighWaterMark} and {@code writeBufferLowWaterMark}.
     *
     * Returns {@code true} if and only if an I/O thread should do its effort
     * to balance the ratio of read and write operations.  Assuring
     * the read-write fairness is sometimes necessary in a high speed network
     * because a certain channel can spend too much time on flushing the
     * large number of write requests not giving enough time for other channels
     * to perform I/O.  The default value is {@code false}.
     */
    @Deprecated
    boolean isReadWriteFair();

    /**
     * @deprecated This property has been replaced by the
     * {@code writeBufferHighWaterMark} and {@code writeBufferLowWaterMark}.
     *
     * Sets if an I/O thread should balance the ratio of read and write
     * operations.  Assuring the read-write fairness is sometimes necessary
     * in a high speed network because a certain channel can spend too much
     * time on flushing the large number of write requests not giving enough
     * time for other channels to perform I/O.  The default value is
     * {@code false}.
     */
    @Deprecated
    void setReadWriteFair(boolean fair);
}
