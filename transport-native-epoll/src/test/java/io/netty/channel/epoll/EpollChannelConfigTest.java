/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.ChannelException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class EpollChannelConfigTest {

    @Test
    public void testOptionGetThrowsChannelException() throws Exception {
        Epoll.ensureAvailability();
        EpollSocketChannel channel = new EpollSocketChannel();
        channel.config().getSoLinger();
        channel.fd().close();
        try {
            channel.config().getSoLinger();
            fail();
        } catch (ChannelException e) {
            // expected
        }
    }

    @Test
    public void testOptionSetThrowsChannelException() throws Exception {
        Epoll.ensureAvailability();
        EpollSocketChannel channel = new EpollSocketChannel();
        channel.config().setKeepAlive(true);
        channel.fd().close();
        try {
            channel.config().setKeepAlive(true);
            fail();
        } catch (ChannelException e) {
            // expected
        }
    }
}
