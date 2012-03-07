/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.util.internal.DetectionUtil;

abstract class SpdyHeaderBlockCompressor {

    static SpdyHeaderBlockCompressor newInstance(
            int compressionLevel, int windowBits, int memLevel) {

        if (DetectionUtil.javaVersion() >= 7) {
            return new SpdyHeaderBlockZlibCompressor(compressionLevel);
        } else {
            return new SpdyHeaderBlockJZlibCompressor(
                    compressionLevel, windowBits, memLevel);
        }
    }

    abstract void setInput(ChannelBuffer decompressed);
    abstract void encode(ChannelBuffer compressed);
    abstract void end();
}
