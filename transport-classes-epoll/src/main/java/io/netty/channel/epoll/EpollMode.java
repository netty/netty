/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.epoll;

/**
 * The <a href="https://linux.die.net//man/7/epoll">epoll</a> mode to use.
 *
 * @deprecated Netty always uses level-triggered mode.
 */
@Deprecated
public enum EpollMode {

    /**
     * Use {@code EPOLLET} (edge-triggered).
     *
     * @see <a href="https://linux.die.net//man/7/epoll">man 7 epoll</a>.
     */
    EDGE_TRIGGERED,

    /**
     * Do not use {@code EPOLLET} (level-triggered).
     *
     * @see <a href="https://linux.die.net//man/7/epoll">man 7 epoll</a>.
     */
    LEVEL_TRIGGERED
}
