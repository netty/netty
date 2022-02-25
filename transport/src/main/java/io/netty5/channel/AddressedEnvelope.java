/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty5.channel;

import io.netty5.util.ReferenceCounted;

import java.net.SocketAddress;

/**
 * A message that wraps another message with a sender address and a recipient address.
 *
 * @implNote AddressedEnvelope implementors likely also implement either {@link ReferenceCounted}
 * or {@link io.netty5.buffer.api.Resource}. Users should be mindful to release or close any address envelopes if
 * that's the case.
 *
 * @param <M> the type of the wrapped message
 * @param <A> the type of the address
 */
public interface AddressedEnvelope<M, A extends SocketAddress> {
    /**
     * Returns the message wrapped by this envelope message.
     */
    M content();

    /**
     * Returns the address of the sender of this message.
     */
    A sender();

    /**
     * Returns the address of the recipient of this message.
     */
    A recipient();
}
