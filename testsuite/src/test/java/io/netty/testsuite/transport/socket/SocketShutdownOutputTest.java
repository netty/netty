/*
 * Copyright 2012 The Netty Project
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
package io.netty.testsuite.transport.socket;

import static org.junit.Assert.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.ByteLoggingHandler;

import java.net.ServerSocket;
import java.net.Socket;

import org.junit.Test;

public class SocketShutdownOutputTest extends AbstractClientSocketTest {

    @Test
    public void testShutdownOutput() throws Throwable {
        run();
    }

    public void testShutdownOutput(Bootstrap cb) throws Throwable {
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        try {
            ss.bind(addr);
            SocketChannel ch = (SocketChannel) cb.handler(new ByteLoggingHandler()).connect().sync().channel();
            assertTrue(ch.isActive());
            assertFalse(ch.isOutputShutdown());

            s = ss.accept();
            ch.write(Unpooled.wrappedBuffer(new byte[] { 1 })).sync();
            assertEquals(1, s.getInputStream().read());

            ch.shutdownOutput();

            assertEquals(-1, s.getInputStream().read());
            assertTrue(s.isConnected());
        } finally {
            if (s != null) {
                s.close();
            }
            ss.close();
        }
    }
}
