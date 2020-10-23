/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.sctp;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.sctp.SctpMessage;
import org.junit.Test;

import java.net.SocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SctpMessageCompletionHandlerTest {

    @Test
    public void testFragmentsReleased() {
        EmbeddedChannel channel = new EmbeddedChannel(new SctpMessageCompletionHandler());
        ByteBuf buffer = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 });
        ByteBuf buffer2 = Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 });
        SctpMessage message = new SctpMessage(new TestMessageInfo(false, 1), buffer);
        assertFalse(channel.writeInbound(message));
        assertEquals(1, buffer.refCnt());
        SctpMessage message2 = new SctpMessage(new TestMessageInfo(false, 2), buffer2);
        assertFalse(channel.writeInbound(message2));
        assertEquals(1, buffer2.refCnt());
        assertFalse(channel.finish());
        assertEquals(0, buffer.refCnt());
        assertEquals(0, buffer2.refCnt());
    }

    private final class TestMessageInfo extends MessageInfo {

        private final boolean complete;
        private final int streamNumber;

        TestMessageInfo(boolean complete, int streamNumber) {
            this.complete = complete;
            this.streamNumber = streamNumber;
        }

        @Override
        public SocketAddress address() {
            return null;
        }

        @Override
        public Association association() {
            return null;
        }

        @Override
        public int bytes() {
            return 0;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public MessageInfo complete(boolean b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnordered() {
            return false;
        }

        @Override
        public MessageInfo unordered(boolean b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int payloadProtocolID() {
            return 0;
        }

        @Override
        public MessageInfo payloadProtocolID(int i) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int streamNumber() {
            return streamNumber;
        }

        @Override
        public MessageInfo streamNumber(int i) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long timeToLive() {
            return 0;
        }

        @Override
        public MessageInfo timeToLive(long l) {
            throw new UnsupportedOperationException();
        }
    }
}
