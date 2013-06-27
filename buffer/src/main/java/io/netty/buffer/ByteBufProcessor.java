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

package io.netty.buffer;

import io.netty.util.Signal;

public interface ByteBufProcessor {
    Signal ABORT = new Signal(ByteBufProcessor.class.getName() + ".ABORT");

    /**
     * @return the number of elements processed. {@link ByteBuf#forEachByte(ByteBufProcessor)} will determine
     *         the index of the next byte to be processed based on this value.  Usually, an implementation will
     *         return {@code 1} to advance the index by {@code 1}.
     */
    int process(ByteBuf buf, int index, byte value) throws Exception;
}
