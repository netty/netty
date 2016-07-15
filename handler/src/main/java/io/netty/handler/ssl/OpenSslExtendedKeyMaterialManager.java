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

import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;

final class OpenSslExtendedKeyMaterialManager extends OpenSslKeyMaterialManager {

    private final X509ExtendedKeyManager keyManager;

    OpenSslExtendedKeyMaterialManager(X509ExtendedKeyManager keyManager, String password) {
        super(keyManager, password);
        this.keyManager = keyManager;
    }

    @Override
    protected String chooseClientAlias(ReferenceCountedOpenSslEngine engine, String[] keyTypes,
                                       X500Principal[] issuer) {
        return keyManager.chooseEngineClientAlias(keyTypes, issuer, engine);
    }

    @Override
    protected String chooseServerAlias(ReferenceCountedOpenSslEngine engine, String type) {
        return keyManager.chooseEngineServerAlias(type, null, engine);
    }
}
