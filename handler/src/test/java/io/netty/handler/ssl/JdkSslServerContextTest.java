/*
 * Copyright 2014 The Netty Project
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

import org.junit.Test;

import javax.net.ssl.SSLException;
import java.io.File;

public class JdkSslServerContextTest {

    @Test
    public void testJdkSslServerWithEncryptedPrivateKey() throws SSLException {
        File keyFile = new File(getClass().getResource("test_encrypted.pem").getFile());
        File crtFile = new File(getClass().getResource("test.crt").getFile());

        new JdkSslServerContext(crtFile, keyFile, "12345");
    }

    @Test
    public void testJdkSslServerWithUnencryptedPrivateKey() throws SSLException {
        File keyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
        File crtFile = new File(getClass().getResource("test.crt").getFile());

        new JdkSslServerContext(crtFile, keyFile, "");
        new JdkSslServerContext(crtFile, keyFile, null);
    }
}
