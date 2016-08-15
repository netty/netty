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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;

@RunWith(Parameterized.class)
public class PemEncodedTest {

    private static PemPrivateKey PEM_ENCODED_PRIVATE_KEY;

    private static PemX509Certificate PEM_ENCODED_CERTIFICATE;

    @Parameters
    public static Collection<SslProvider> providers() {
        return Arrays.asList(SslProvider.OPENSSL, SslProvider.OPENSSL_REFCNT);
    }

    private final SslProvider provider;

    public PemEncodedTest(SslProvider provider) {
        this.provider = provider;
    }

    @BeforeClass
    public static void init() throws Exception {
        assumeTrue(OpenSsl.isAvailable());

        SelfSignedCertificate ssc = new SelfSignedCertificate();
        try {
            PEM_ENCODED_PRIVATE_KEY = PemPrivateKey.valueOf(toByteArray(ssc.privateKey()));
            PEM_ENCODED_CERTIFICATE = PemX509Certificate.valueOf(toByteArray(ssc.certificate()));
        } finally {
            ssc.delete();
        }
    }

    @Test
    public void testPemEncoded() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        SslContext context = SslContextBuilder.forServer(PEM_ENCODED_PRIVATE_KEY, PEM_ENCODED_CERTIFICATE)
                .sslProvider(provider)
                .build();
        try {
            assertTrue("context=" + context, context instanceof ReferenceCountedOpenSslContext);
        } finally {
            ReferenceCountUtil.release(context);
        }
    }

    private static byte[] toByteArray(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[1024];
                int len = -1;
                while ((len = in.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
            } finally {
                baos.close();
            }

            return baos.toByteArray();
        } finally {
            in.close();
        }
    }
}
