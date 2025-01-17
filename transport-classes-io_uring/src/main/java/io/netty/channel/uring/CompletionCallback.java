/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

interface CompletionCallback {
    /**
     * Called for a completion event that was put into the {@link CompletionQueue}.
     *
     * @param res   the result of the completion event.
     * @param flags the flags
     * @param udata the user data that was provided as part of the submission
     * @return      {@code true} if we more data (in a loop) can be handled be this callback, {@code false} otherwise.
     */
    boolean handle(int res, int flags, long udata);
}
