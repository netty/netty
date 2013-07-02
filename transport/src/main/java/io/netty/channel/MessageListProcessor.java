/*
 * Copyright 2013 The Netty Project
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

package io.netty.channel;

import io.netty.util.Signal;

public interface MessageListProcessor<T> {

    Signal ABORT = new Signal(MessageListProcessor.class.getName() + ".ABORT");

    /**
     * @return the number of elements processed. {@link MessageList#forEach(MessageListProcessor)} will determine
     *         the index of the next element to be processed based on this value.  Usually, an implementation will
     *         return {@code 1} to advance the index by {@code 1}.  Note that returning a non-positive value is
     *         allowed where a negative value advances the index in the opposite direction and zero leaves the index
     *         as-is.
     */
    int process(T value) throws Exception;
}
