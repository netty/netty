/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketHalfClosedTest;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringSocketHalfClosedTest extends SocketHalfClosedTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.socket();
    }

    @Disabled
    @Test
    public void testAutoCloseFalseDoesShutdownOutput(TestInfo testInfo) throws Throwable {
        // This test only works on Linux / BSD / MacOS as we assume some semantics that are not true for Windows.
        Assumptions.assumeFalse(PlatformDependent.isWindows());
        this.run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testAutoCloseFalseDoesShutdownOutput(serverBootstrap, bootstrap);
            }
        });
    }
}
