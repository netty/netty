/*
 * Copyright 2018 The Netty Project
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

import java.util.List;
import java.util.Set;

/**
 * This class behaves like {@link IdentityCipherSuiteFilter} except that it falls back to supportedCiphers
 * instead of defaultCiphers when ciphers are null.
 *
 * Typical usage is to enable weak and insecure ciphers that are supported but filtered out in defaultCiphers.
 */
public final class IdentityWithSupportedFallbackCipherSuiteFilter implements CipherSuiteFilter {
    public static final IdentityWithSupportedFallbackCipherSuiteFilter INSTANCE =
            new IdentityWithSupportedFallbackCipherSuiteFilter();

    private IdentityWithSupportedFallbackCipherSuiteFilter() { }

    @Override
    public String[] filterCipherSuites(Iterable<String> ciphers,
                                       List<String> defaultCiphers,
                                       Set<String> supportedCiphers) {
        return ciphers == null ?
                supportedCiphers.toArray(new String[supportedCiphers.size()]) :
                IdentityCipherSuiteFilter.INSTANCE.filterCipherSuites(ciphers, defaultCiphers, supportedCiphers);
    }
}
