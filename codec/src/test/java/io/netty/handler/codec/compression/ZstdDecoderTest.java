/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.compression;

import com.github.luben.zstd.ZstdOutputStream;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;

public class ZstdDecoderTest extends AbstractDecoderTest {

    public ZstdDecoderTest() throws Exception {
    }

    @Override
    public EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new ZstdDecoder());
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ZstdOutputStream zstdOs = new ZstdOutputStream(os);
        zstdOs.write(data);
        zstdOs.close();

        return os.toByteArray();
    }
}
