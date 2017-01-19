/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketRstTest;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KQueueSocketRstTest extends SocketRstTest {
    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return KQueueSocketTestPermutation.INSTANCE.socket();
    }

    @Override
    protected void assertRstOnCloseException(IOException cause, Channel clientChannel) {
        if (!AbstractKQueueChannel.class.isInstance(clientChannel)) {
            super.assertRstOnCloseException(cause, clientChannel);
            return;
        }

        assertTrue("actual [type, message]: [" + cause.getClass() + ", " + cause.getMessage() + "]",
                   cause instanceof NativeIoException);
        assertEquals(Errors.ERRNO_ECONNRESET_NEGATIVE, ((NativeIoException) cause).expectedErr());
    }
}
