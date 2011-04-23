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
package org.jboss.netty.channel.socket.sctp;

import org.jboss.netty.channel.ReceiveBufferSizePredictor;
import org.jboss.netty.channel.ReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.socket.SocketChannelConfig;

/**
 * A {@link org.jboss.netty.channel.socket.SocketChannelConfig} for a NIO TCP/IP {@link org.jboss.netty.channel.socket.SocketChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link org.jboss.netty.channel.ChannelConfig} and
 * {@link org.jboss.netty.channel.socket.SocketChannelConfig}, {@link org.jboss.netty.channel.socket.sctp.NioSocketChannelConfig} allows the
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
 * <td>{@code "receiveBufferSizePredictor"}</td><td>{@link #setReceiveBufferSizePredictor(org.jboss.netty.channel.ReceiveBufferSizePredictor)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSizePredictorFactory"}</td><td>{@link #setReceiveBufferSizePredictorFactory(org.jboss.netty.channel.ReceiveBufferSizePredictorFactory)}</td>
 * </tr>
 * </table>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 */
public interface NioSocketChannelConfig extends SocketChannelConfig {

    /**
     * Returns the high water mark of the write buffer.  If the number of bytes
     * queued in the write buffer exceeds this value, {@link org.jboss.netty.channel.Channel#isWritable()}
     * will start to return {@code false}.
     */
    int getWriteBufferHighWaterMark();

    /**
     * Sets the high water mark of the write buffer.  If the number of bytes
     * queued in the write buffer exceeds this value, {@link org.jboss.netty.channel.Channel#isWritable()}
     * will start to return {@code false}.
     */
    void setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    /**
     * Returns the low water mark of the write buffer.  Once the number of bytes
     * queued in the write buffer exceeded the
     * {@linkplain #setWriteBufferHighWaterMark(int) high water mark} and then
     * dropped down below this value, {@link org.jboss.netty.channel.Channel#isWritable()} will return
     * {@code true} again.
     */
    int getWriteBufferLowWaterMark();

    /**
     * Sets the low water mark of the write buffer.  Once the number of bytes
     * queued in the write buffer exceeded the
     * {@linkplain #setWriteBufferHighWaterMark(int) high water mark} and then
     * dropped down below this value, {@link org.jboss.netty.channel.Channel#isWritable()} will return
     * {@code true} again.
     */
    void setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    /**
     * Returns the maximum loop count for a write operation until
     * {@link java.nio.channels.WritableByteChannel#write(java.nio.ByteBuffer)} returns a non-zero value.
     * It is similar to what a spin lock is used for in concurrency programming.
     * It improves memory utilization and write throughput depending on
     * the platform that JVM runs on.  The default value is {@code 16}.
     */
    int getWriteSpinCount();

    /**
     * Sets the maximum loop count for a write operation until
     * {@link java.nio.channels.WritableByteChannel#write(java.nio.ByteBuffer)} returns a non-zero value.
     * It is similar to what a spin lock is used for in concurrency programming.
     * It improves memory utilization and write throughput depending on
     * the platform that JVM runs on.  The default value is {@code 16}.
     *
     * @throws IllegalArgumentException
     *         if the specified value is {@code 0} or less than {@code 0}
     */
    void setWriteSpinCount(int writeSpinCount);

    /**
     * Returns the {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
     */
    ReceiveBufferSizePredictor getReceiveBufferSizePredictor();

    /**
     * Sets the {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
     */
    void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);

    /**
     * Returns the {@link org.jboss.netty.channel.ReceiveBufferSizePredictorFactory} which creates a new
     * {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(org.jboss.netty.channel.ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
     */
    ReceiveBufferSizePredictorFactory getReceiveBufferSizePredictorFactory();

    /**
     * Sets the {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} which creates a new
     * {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link org.jboss.netty.channel.ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(org.jboss.netty.channel.ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
     */
    void setReceiveBufferSizePredictorFactory(
            ReceiveBufferSizePredictorFactory predictorFactory);
}
