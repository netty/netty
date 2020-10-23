/*
 * Copyright 2017 The Netty Project
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

package io.netty.handler.ssl;

import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class DelegatingSslContextTest {
    private static final String[] EXPECTED_PROTOCOLS = { SslUtils.PROTOCOL_TLS_V1_1 };

    @Test
    public void testInitEngineOnNewEngine() throws Exception {
        SslContext delegating = newDelegatingSslContext();

        SSLEngine engine = delegating.newEngine(UnpooledByteBufAllocator.DEFAULT);
        Assert.assertArrayEquals(EXPECTED_PROTOCOLS, engine.getEnabledProtocols());

        engine = delegating.newEngine(UnpooledByteBufAllocator.DEFAULT, "localhost", 9090);
        Assert.assertArrayEquals(EXPECTED_PROTOCOLS, engine.getEnabledProtocols());
    }

    @Test
    public void testInitEngineOnNewSslHandler() throws Exception {
        SslContext delegating = newDelegatingSslContext();

        SslHandler handler = delegating.newHandler(UnpooledByteBufAllocator.DEFAULT);
        Assert.assertArrayEquals(EXPECTED_PROTOCOLS, handler.engine().getEnabledProtocols());

        handler = delegating.newHandler(UnpooledByteBufAllocator.DEFAULT, "localhost", 9090);
        Assert.assertArrayEquals(EXPECTED_PROTOCOLS, handler.engine().getEnabledProtocols());
    }

    private static SslContext newDelegatingSslContext() throws Exception {
        return new DelegatingSslContext(new JdkSslContext(SSLContext.getDefault(), false, ClientAuth.NONE)) {
            @Override
            protected void initEngine(SSLEngine engine) {
                engine.setEnabledProtocols(EXPECTED_PROTOCOLS);
            }
        };
    }
}
