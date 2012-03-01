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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictor;
import org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;
import org.jboss.netty.channel.ReceiveBufferSizePredictorFactory;
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
 * <td>{@code "writeBufferHighWaterMark"}</td><td>{@link #setWriteBufferHighWaterMark(int)}</td>
 * </tr><tr>
 * <td>{@code "writeBufferLowWaterMark"}</td><td>{@link #setWriteBufferLowWaterMark(int)}</td>
 * </tr><tr>
 * <td>{@code "writeSpinCount"}</td><td>{@link #setWriteSpinCount(int)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSizePredictor"}</td><td>{@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSizePredictorFactory"}</td><td>{@link #setReceiveBufferSizePredictorFactory(ReceiveBufferSizePredictorFactory)}</td>
 * </tr>
 * </table>
 */
public interface NioSocketChannelConfig extends SocketChannelConfig, NioChannelConfig {

    /**
     * Returns the {@link ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
     */
    ReceiveBufferSizePredictor getReceiveBufferSizePredictor();

    /**
     * Sets the {@link ReceiveBufferSizePredictor} which predicts the
     * number of readable bytes in the socket receive buffer.  The default
     * predictor is <tt>{@link AdaptiveReceiveBufferSizePredictor}(64, 1024, 65536)</tt>.
     */
    void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);

    /**
     * Returns the {@link ReceiveBufferSizePredictorFactory} which creates a new
     * {@link ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
     */
    ReceiveBufferSizePredictorFactory getReceiveBufferSizePredictorFactory();

    /**
     * Sets the {@link ReceiveBufferSizePredictor} which creates a new
     * {@link ReceiveBufferSizePredictor} when a new channel is created and
     * no {@link ReceiveBufferSizePredictor} was set.  If no predictor was set
     * for the channel, {@link #setReceiveBufferSizePredictor(ReceiveBufferSizePredictor)}
     * will be called with the new predictor.  The default factory is
     * <tt>{@link AdaptiveReceiveBufferSizePredictorFactory}(64, 1024, 65536)</tt>.
     */
    void setReceiveBufferSizePredictorFactory(ReceiveBufferSizePredictorFactory predictorFactory);
}
