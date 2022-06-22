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
 * The direction of a shutdown. Depending on the direction this has different meaning.
 * If the {@link #Inbound} direction is shutdown, no more data is read from the transport and dispatched.
 * If the {@link #Outbound} direction is shutdown, no more writes will be possible to the transport and so all writes
 * will fail.
 */
public enum ChannelShutdownDirection {
    /**
     * The inbound direction of a {@link Channel} was or should be shutdown.
     */
    Inbound,
    /**
     * The outbound direction of a {@link Channel} was or should be shutdown.
     */
    Outbound
}
