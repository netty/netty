/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.h2new;

/**
 * A distributor of <a href="https://httpwg.org/specs/rfc7540.html#WINDOW_UPDATE">flow controlled bytes</a>.
 * Implementations are expected to intercept appropriate HTTP/2 frames to build the current flow controlled state for
 * the channel as well as a stream.
 */
public interface ChannelFlowControlledBytesDistributor {
    /**
     * Registers a {@link DistributionAcceptor} for the passed {@code streamId}. This {@link DistributionAcceptor} will
     * accept flow control credits that are sent by the remote peer and then optionally distributed to active
     * streams by this {@link ChannelFlowControlledBytesDistributor}.
     * <p>
     * Only one {@link DistributionAcceptor} per stream is allowed. In case, there already exists an
     * {@link DistributionAcceptor} for this {@code streamId}, it will be
     * {@link DistributionAcceptor#dispose() disposed} and the pending flow control credits will be distributed to the
     * new {@link DistributionAcceptor}.
     *
     * @param streamId for which the {@link DistributionAcceptor} is to be used.
     * @param acceptor for the passed {@code streamId}
     * @return Old {@link DistributionAcceptor} for the passed {@code streamId}, {@code null} if none exists.
     * @throws IllegalArgumentException if the caller is not the eventloop for the associated channel.
     *
     * @see  #replaceLocal(int, DistributionAcceptor)
     */
    DistributionAcceptor replaceRemote(int streamId, DistributionAcceptor acceptor);

    /**
     * Registers a {@link DistributionAcceptor} for the passed {@code streamId}. This {@link DistributionAcceptor} will
     * accept flow control credits that are calculated by the local peer and then distributed to active
     * streams by this {@link ChannelFlowControlledBytesDistributor}.
     * <p>
     * Only one {@link DistributionAcceptor} per stream is allowed. In case, there already exists an
     * {@link DistributionAcceptor} for this {@code streamId}, it will be
     * {@link DistributionAcceptor#dispose() disposed} and the pending flow control credits will be distributed to the
     * new {@link DistributionAcceptor}.
     *
     * @param streamId for which the {@link DistributionAcceptor} is to be used.
     * @param acceptor for the passed {@code streamId}
     * @return Old {@link DistributionAcceptor} for the passed {@code streamId}, {@code null} if none exists.
     * @throws IllegalArgumentException if the caller is not the eventloop for the associated channel.
     *
     * @see  #replaceRemote(int, DistributionAcceptor)
     */
    DistributionAcceptor replaceLocal(int streamId, DistributionAcceptor acceptor);

    /**
     * An acceptor of flow control credits for a specific stream.
     */
    interface DistributionAcceptor {
        /**
         * Accumulate the passed number of byte to the existing flow controlled bytes for this stream.
         *
         * @param accumulate Number of byte to accumulate to the existing flow controlled bytes for this stream. This
         * can be positive or negative, such that negative value indicates decrement and positive indicates increment of
         * the flow control credits.
         */
        void accumulate(int accumulate);

        /**
         * Disposes this acceptor which may happen due to stream closures or replacement of the acceptor.
         *
         * @return Unused bytes from the assigned bytes.
         */
        int dispose();
    }
}
