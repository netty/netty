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

package org.jboss.netty.channel.socket;

/**
 * A {@link Worker} is responsible to dispatch IO operations
 *
 */
public interface Worker extends Runnable {

    /**
     * Execute the given {@link Runnable} in the IO-Thread. This may be now or
     * later once the IO-Thread do some other work.
     *
     * @param task
     *            the {@link Runnable} to execute
     */
    void executeInIoThread(Runnable task);

}
