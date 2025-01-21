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
package io.netty.handler.ssl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.net.ssl.SSLException;
import java.io.File;

public class JdkSslServerContextTest extends SslContextTest {

    @Override
    protected SslContext newSslContext(File crtFile, File keyFile, String pass) throws SSLException {
        return new JdkSslServerContext(crtFile, keyFile, pass);
    }

    @Test
    void testWrappingOfTrustManager() {
        Assertions.assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                JdkSslServerContext.checkIfWrappingTrustManagerIsSupported();
            }
        });
    }
}
