/*
 * Copyright 2016 The Netty Project
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketRstTest;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollSocketRstTest extends SocketRstTest {
    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.socketWithoutFastOpen();
    }

    @Override
    protected void assertRstOnCloseException(IOException cause, Channel clientChannel) {
        if (!AbstractEpollChannel.class.isInstance(clientChannel)) {
            super.assertRstOnCloseException(cause, clientChannel);
            return;
        }

        assertTrue(cause instanceof NativeIoException,
            "actual [type, message]: [" + cause.getClass() + ", " + cause.getMessage() + "]");
        assertEquals(Errors.ERRNO_ECONNRESET_NEGATIVE, ((NativeIoException) cause).expectedErr());
    }
}
