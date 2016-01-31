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

import io.netty.util.internal.InternalThreadLocalMap;

import java.util.List;
import java.util.Set;

/**
 * This class will not do any filtering of ciphers suites.
 */
public final class IdentityCipherSuiteFilter implements CipherSuiteFilter {
    public static final IdentityCipherSuiteFilter INSTANCE = new IdentityCipherSuiteFilter();

    private IdentityCipherSuiteFilter() { }

    @Override
    public String[] filterCipherSuites(Iterable<String> ciphers, List<String> defaultCiphers,
            Set<String> supportedCiphers) {
        if (ciphers == null) {
            return defaultCiphers.toArray(new String[defaultCiphers.size()]);
        } else {
            List<String> newCiphers = InternalThreadLocalMap.get().arrayList(supportedCiphers.size());
            for (String c : ciphers) {
                if (c == null) {
                    break;
                }
                newCiphers.add(c);
            }
            return newCiphers.toArray(new String[newCiphers.size()]);
        }
    }

}
