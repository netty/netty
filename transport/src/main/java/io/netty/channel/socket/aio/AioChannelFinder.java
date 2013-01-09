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
package io.netty.channel.socket.aio;

/**
 * Allow to fine the {@link AbstractAioChannel} for a task.
 */
interface AioChannelFinder {

    /**
     * Try to find the {@link AbstractAioChannel} for the given {@link Runnable}.
     *
     * @param   command         the {@link Runnable} for which the {@link AbstractAioChannel} should be found.
     * @return  channel         the {@link AbstractAioChannel} which belongs to the {@link Runnable} or {@code null} if
     *                          it could not found.
     * @throws  Exception       will get thrown if an error accours.
     */
    AbstractAioChannel findChannel(Runnable command) throws Exception;
}
