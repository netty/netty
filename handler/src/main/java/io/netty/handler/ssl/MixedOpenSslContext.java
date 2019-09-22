/*
 * Copyright 2019 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;

import java.security.cert.Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/**
 * This class implements a MIXED mode for OpenSSL engines and contexts. It will use a finalizer to
 * ensure native resources backing this context are automatically cleaned up, but it will rely on
 * manual release for its produced {@link ReferenceCountedOpenSslEngine} instances.
 */
public abstract class MixedOpenSslContext extends ReferenceCountedOpenSslContext {
  MixedOpenSslContext(Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apnCfg,
                 long sessionCacheSize, long sessionTimeout, int mode, Certificate[] keyCertChain,
                 ClientAuth clientAuth, String[] protocols, boolean startTls, boolean enableOcsp)
      throws SSLException {
    super(ciphers, cipherFilter, apnCfg, sessionCacheSize, sessionTimeout, mode, keyCertChain,
        clientAuth, protocols, startTls, enableOcsp, false);
  }

  @Override
  final SSLEngine newEngine0(ByteBufAllocator alloc, String peerHost, int peerPort, boolean jdkCompatibilityMode) {
    return new ReferenceCountedOpenSslEngine(this, alloc, peerHost, peerPort, jdkCompatibilityMode, true);
  }

  @Override
  @SuppressWarnings("FinalizeDeclaration")
  protected final void finalize() throws Throwable {
    super.finalize();
    OpenSsl.releaseIfNeeded(this);
  }
}
