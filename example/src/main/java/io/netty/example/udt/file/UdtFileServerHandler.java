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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.stream.ChunkedFile;

import java.io.RandomAccessFile;

/**
 * A simple handler that serves incoming the String which is a name of a file
 * and sends its contents back.
 */
public class UdtFileServerHandler extends SimpleChannelInboundHandler<String> {

    private UdtFileMessage response;
    private static final String welcomeMsg = "[HELLO]: Type the name of a file to download.";

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        response = createMsgResponse(welcomeMsg);
        ctx.writeAndFlush(response);
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {

        // Close the connection if the client has sent 'bye'.
        if ("bye".equals(msg.toLowerCase())) {
            ctx.close();
            return;
        }

        RandomAccessFile raf = null;
        long length = -1;
        try {
            raf = new RandomAccessFile(msg, "r");
            length = raf.length();
        } catch (Exception e) {
            String errorMsg = "[ERROR]: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            response = createMsgResponse(errorMsg);
            ctx.writeAndFlush(response);
            return;
        } finally {
            if (length < 0 && raf != null) {
                raf.close();
            }
        }

        // Builds a response message and write to the ctx.
        response = createFileResponse(msg, (int) length);
        ctx.write(response);

        // Writes the file to the ctx
        ctx.write(new ChunkedFile(raf));
        ctx.flush();
    }

    private UdtFileMessage createMsgResponse(String msg) {
        UdtFileMessage response = new UdtFileMessage();
        response.setOpcode(UdtFileOpcodes.SND_MSG);
        response.setMessageLength(msg.length());
        response.setMessage(msg);
        return response;
    }

    private UdtFileMessage createFileResponse(String fileName, int fileLength) {
        UdtFileMessage response = new UdtFileMessage();
        response.setOpcode(UdtFileOpcodes.SND_FILE);
        response.setMessageLength(fileName.length());
        response.setFileLength(fileLength);
        response.setMessage(fileName);
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
