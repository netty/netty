/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

public class Http2SecurityUtilTest {

    @Test
    public void testTLSv13CiphersIncluded() throws SSLException {
        Assume.assumeTrue(SslProvider.isTlsv13Supported(SslProvider.JDK));
        testCiphersIncluded("TLSv1.3");
    }

    @Test
    public void testTLSv12CiphersIncluded() throws SSLException  {
        testCiphersIncluded("TLSv1.2");
    }

    private static void testCiphersIncluded(String protocol) throws SSLException  {
        SslContext context = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).protocols(protocol)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE).build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        Assert.assertTrue("No " + protocol + " ciphers found", engine.getEnabledCipherSuites().length > 0);
    }
}
