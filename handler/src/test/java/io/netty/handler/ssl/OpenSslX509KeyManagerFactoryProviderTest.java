/*
 * Copyright 2018 The Netty Project
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

import javax.net.ssl.KeyManagerFactory;
import java.security.KeyStore;

public class OpenSslX509KeyManagerFactoryProviderTest extends OpenSslCachingKeyMaterialProviderTest {

    @Override
    protected KeyManagerFactory newKeyManagerFactory() throws Exception {
        char[] password = PASSWORD.toCharArray();
        final KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(getClass().getResourceAsStream("mutual_auth_server.p12"), password);

        OpenSslX509KeyManagerFactory kmf = new OpenSslX509KeyManagerFactory();
        kmf.init(keystore, password);
        return kmf;
    }

    @Override
    protected OpenSslKeyMaterialProvider newMaterialProvider(KeyManagerFactory kmf, String password) {
        return ((OpenSslX509KeyManagerFactory) kmf).newProvider();
    }
}
