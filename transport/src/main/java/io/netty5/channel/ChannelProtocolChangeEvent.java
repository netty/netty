/*
 * Copyright 2022 The Netty Project
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

/**
 * An event that is triggered by an protocol. This for example might be a protocol upgrade / downgrade.
 *
 * @param <T>
 */
public interface ChannelProtocolChangeEvent<T> {

    /**
     * Returns {@code true} if and only if the operation was completed successfully.
     */
    boolean isSuccess();

    /**
     * Returns {@code true} if and only if the operation was completed and failed.
     */
    boolean isFailed();

    /**
     * Returns the cause of the failed protocol event.
     *
     * @return The cause of the failure, if any. Otherwise {@code null} if succeeded.
     */
    Throwable cause();

    /**
     * Extra protocol data for this event. This might be {@code null} if there was no extra data to be included.
     *
     * @return protocol specific data.
     */
    T protocolData();
}
