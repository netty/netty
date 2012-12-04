/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.channel.socket.Worker;

import java.nio.channels.Selector;

/**
 * The {@link WorkerPool} is responsible to hand of {@link Worker}'s on demand
 *
 */
public interface WorkerPool<E extends Worker> {

    /**
     * Return the next {@link Worker} to use
     *
     * @return worker
     */
    E nextWorker();

    /**
     * Replaces the current {@link Selector}s of the {@link Worker}s with new {@link Selector}s to work around the
     * infamous epoll 100% CPU bug.
     */
    void rebuildSelectors();
}
