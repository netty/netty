/*
 * Copyright 2015 The Netty Project
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
package io.netty.example.udt.file;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A simple handler that receives a file or a message from a server.
 */
public class UdtFileClientHandler extends SimpleChannelInboundHandler<Object> {

    FileOutputStream fos;
    FileChannel fch;
    String copiedFileName;

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Creates a file.
        if (msg instanceof File) {
            copiedFileName = "copy_of_" + ((File) msg).getName();
            fos = new FileOutputStream(copiedFileName);
            fch = fos.getChannel();
            return;
        }

        // File writing.
        if (msg instanceof ByteBuf) {
            ByteBuf in = (ByteBuf) msg;
            ByteBuffer nioBuffer = in.nioBuffer();
            while (nioBuffer.hasRemaining()) {
                fch.write(nioBuffer);
            }
            return;
        }

        // Closing the file.
        if (msg instanceof LastUdtFileMessage) {
            fch.close();
            fos.close();
            System.out.println(">> " + copiedFileName + " is created.");
            return;
        }

        // Prints out a message from a server.
        if (msg instanceof String) {
            System.out.println(msg);
            return;
        }
    }
}
