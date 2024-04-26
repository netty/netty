/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.ssl.util;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

final class X509KeyManagerWrapper extends X509ExtendedKeyManager {

    private final X509KeyManager delegate;

    X509KeyManagerWrapper(X509KeyManager delegate) {
        this.delegate = checkNotNull(delegate, "delegate");
    }

    @Override
    public String[] getClientAliases(String var1, Principal[] var2) {
        return delegate.getClientAliases(var1, var2);
    }

    @Override
    public String chooseClientAlias(String[] var1, Principal[] var2, Socket var3) {
        return delegate.chooseClientAlias(var1, var2, var3);
    }

    @Override
    public String[] getServerAliases(String var1, Principal[] var2) {
        return delegate.getServerAliases(var1, var2);
    }

    @Override
    public String chooseServerAlias(String var1, Principal[] var2, Socket var3) {
        return delegate.chooseServerAlias(var1, var2, var3);
    }

    @Override
    public X509Certificate[] getCertificateChain(String var1) {
        return delegate.getCertificateChain(var1);
    }

    @Override
    public PrivateKey getPrivateKey(String var1) {
        return delegate.getPrivateKey(var1);
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        return delegate.chooseClientAlias(keyType, issuers, null);
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        return delegate.chooseServerAlias(keyType, issuers, null);
    }
}
