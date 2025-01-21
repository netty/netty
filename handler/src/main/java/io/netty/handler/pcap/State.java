/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.pcap;

/**
 * The state of the {@link PcapWriteHandler}.
 */
enum State {

    /**
     * The handler is not active.
     */
    INIT,

    /**
     * The handler is active and actively writing Pcap data.
     */
    WRITING,

    /**
     * The handler is paused. No Pcap data will be written.
     */
    PAUSED,

    /**
     * The handler is closed.
     */
    CLOSED
}
