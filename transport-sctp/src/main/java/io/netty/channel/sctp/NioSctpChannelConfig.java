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

import io.netty.channel.ReceiveBufferSizePredictor;
import io.netty.channel.ReceiveBufferSizePredictorFactory;
import io.netty.channel.socket.nio.NioChannelConfig;

/**
 * A {@link io.netty.channel.sctp.SctpChannelConfig} for a NIO SCTP/IP {@link io.netty.channel.sctp.SctpChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link io.netty.channel.ChannelConfig} and
 * {@link io.netty.channel.sctp.SctpChannelConfig}, {@link io.netty.channel.sctp.NioSctpChannelConfig} allows the
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
 * <td>{@code "receiveBufferSizePredictor"}</td><td>{@link #setReceiveBufferSizePredictor(io.netty.channel.ReceiveBufferSizePredictor)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSizePredictorFactory"}</td><td>{@link #setReceiveBufferSizePredictorFactory(io.netty.channel.ReceiveBufferSizePredictorFactory)}</td>
 * </tr>
 * </table>
 */
public interface NioSctpChannelConfig extends SctpChannelConfig, NioChannelConfig {

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
     * Returns the {@link io.netty.channel.ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link io.netty.channel.AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
     */
    ReceiveBufferSizePredictor getReceiveBufferSizePredictor();

    /**
     * Sets the {@link io.netty.channel.ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link io.netty.channel.AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
     */
    void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);

    /**
     * Returns the {@link io.netty.channel.ReceiveBufferSizePredictorFactory} which creates a new
     * {@link io.netty.channel.ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link io.netty.channel.ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(io.netty.channel.ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link io.netty.channel.AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
     */
    ReceiveBufferSizePredictorFactory getReceiveBufferSizePredictorFactory();

    /**
     * Sets the {@link io.netty.channel.ReceiveBufferSizePredictor} which creates a new
     * {@link io.netty.channel.ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link io.netty.channel.ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(io.netty.channel.ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link io.netty.channel.AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
     */
    void setReceiveBufferSizePredictorFactory(
            ReceiveBufferSizePredictorFactory predictorFactory);
}
