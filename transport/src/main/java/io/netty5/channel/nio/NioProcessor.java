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
package io.netty5.channel.nio;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;


/**
 * Process IO.
 */
interface NioProcessor {

    /**
     * Register to a {@link Selector}.
     *
     * @param selector                  the {@link Selector} to register to.
     * @throws ClosedChannelException   if already closed.
     */
    void register(Selector selector) throws ClosedChannelException;

    /**
     * Deregister from previous registered {@link Selector}.
     */
    void deregister();

    /**
     * Handle some IO for the given {@link SelectionKey}.
     *
     * @param key   the {@link SelectionKey} that needs to be handled.
     */
    void handle(SelectionKey key);

    /**
     * Close this processor.
     */
    void close();
}
