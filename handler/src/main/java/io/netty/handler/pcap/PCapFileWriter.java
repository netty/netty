/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class PCapFileWriter implements Closeable {
    private final long myStartTime = System.nanoTime();
    private final FileOutputStream fileOutputStream;

    public PCapFileWriter(File file) throws IOException {
        fileOutputStream = new FileOutputStream(file);

        ByteBuf byteBuf = Unpooled.buffer();
        fileOutputStream.write(ByteBufUtil.getBytes( PcapHeaders.generateGlobalHeader(byteBuf)));
    }

    public void writePacket(ByteBuf packet) throws IOException {
        long difference = System.nanoTime() - myStartTime;

        ByteBuf byteBuf = PcapHeaders.generatePacketHeader(
                Unpooled.buffer(),
                (int) (difference / 1000000000),
                (int) difference / 1000000,
                packet.readableBytes(),
                packet.readableBytes()
        );

        fileOutputStream.write(ByteBufUtil.getBytes(byteBuf));
        fileOutputStream.write(ByteBufUtil.getBytes(packet));
    }

    @Override
    public void close() throws IOException {
        this.fileOutputStream.close();
    }
}
