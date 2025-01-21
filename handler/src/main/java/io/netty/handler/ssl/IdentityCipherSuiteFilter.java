/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.internal.EmptyArrays;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This class will not do any filtering of ciphers suites.
 */
public final class IdentityCipherSuiteFilter implements CipherSuiteFilter {

    /**
     * Defaults to default ciphers when provided ciphers are null
     */
    public static final IdentityCipherSuiteFilter INSTANCE = new IdentityCipherSuiteFilter(true);

    /**
     * Defaults to supported ciphers when provided ciphers are null
     */
    public static final IdentityCipherSuiteFilter INSTANCE_DEFAULTING_TO_SUPPORTED_CIPHERS =
            new IdentityCipherSuiteFilter(false);

    private final boolean defaultToDefaultCiphers;

    private IdentityCipherSuiteFilter(boolean defaultToDefaultCiphers) {
        this.defaultToDefaultCiphers = defaultToDefaultCiphers;
    }

    @Override
    public String[] filterCipherSuites(Iterable<String> ciphers, List<String> defaultCiphers,
            Set<String> supportedCiphers) {
        if (ciphers == null) {
            return defaultToDefaultCiphers ?
                    defaultCiphers.toArray(EmptyArrays.EMPTY_STRINGS) :
                    supportedCiphers.toArray(EmptyArrays.EMPTY_STRINGS);
        } else {
            List<String> newCiphers = new ArrayList<String>(supportedCiphers.size());
            for (String c : ciphers) {
                if (c == null) {
                    break;
                }
                newCiphers.add(c);
            }
            return newCiphers.toArray(EmptyArrays.EMPTY_STRINGS);
        }
    }
}
