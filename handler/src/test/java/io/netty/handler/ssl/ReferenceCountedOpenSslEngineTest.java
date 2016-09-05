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
package io.netty.handler.ssl;

import io.netty.util.ReferenceCountUtil;

import javax.net.ssl.SSLEngine;

public class ReferenceCountedOpenSslEngineTest extends OpenSslEngineTest {
    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.OPENSSL_REFCNT;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.OPENSSL_REFCNT;
    }

    @Override
    protected void cleanupClientSslContext(SslContext ctx) {
        ReferenceCountUtil.release(ctx);
    }

    @Override
    protected void cleanupClientSslEngine(SSLEngine engine) {
        ReferenceCountUtil.release(engine);
    }

    @Override
    protected void cleanupServerSslContext(SslContext ctx) {
        ReferenceCountUtil.release(ctx);
    }

    @Override
    protected void cleanupServerSslEngine(SSLEngine engine) {
        ReferenceCountUtil.release(engine);
    }
}
