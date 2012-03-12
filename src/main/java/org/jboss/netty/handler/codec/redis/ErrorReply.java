/*
 * Copyright 2011 The Netty Project
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
package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

/**
 * {@link Reply} which will be returned if an error was detected
 * 
 *
 */
public class ErrorReply extends Reply {
    public static final char MARKER = '-';
    private static final byte[] ERR = "ERR ".getBytes();
    public final ChannelBuffer error;

    public ErrorReply(ChannelBuffer error) {
        this.error = error;
    }

    @Override
    public void write(ChannelBuffer os) throws IOException {
        os.writeByte(MARKER);
        os.writeBytes(ERR);
        os.writeBytes(error, 0, error.readableBytes());
        os.writeBytes(Command.CRLF);
    }
}
