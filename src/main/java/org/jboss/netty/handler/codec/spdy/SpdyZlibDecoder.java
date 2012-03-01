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
/*
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.compression.CompressionException;
import org.jboss.netty.util.internal.jzlib.JZlib;
import org.jboss.netty.util.internal.jzlib.ZStream;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

class SpdyZlibDecoder {

    private final byte[] out = new byte[8192];
    private final ZStream z = new ZStream();

    public SpdyZlibDecoder() {
        int resultCode;
        resultCode = z.inflateInit(JZlib.W_ZLIB);
        if (resultCode != JZlib.Z_OK) {
            throw new CompressionException("ZStream initialization failure");
        }
        z.next_out = out;
    }

    public void setInput(ChannelBuffer compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.readBytes(in);
        z.next_in = in;
        z.next_in_index = 0;
        z.avail_in = in.length;
    }

    public void decode(ChannelBuffer decompressed) {
        z.next_out_index = 0;
        z.avail_out = out.length;

        int resultCode = z.inflate(JZlib.Z_SYNC_FLUSH);

        if (resultCode == JZlib.Z_NEED_DICT) {
            resultCode = z.inflateSetDictionary(SPDY_DICT, SPDY_DICT.length);
            if (resultCode != JZlib.Z_OK) {
                throw new CompressionException("ZStream dictionary failure");
            }
            z.inflate(JZlib.Z_SYNC_FLUSH);
        }

        if (z.next_out_index > 0) {
            decompressed.writeBytes(out, 0, z.next_out_index);
        }
    }
}
