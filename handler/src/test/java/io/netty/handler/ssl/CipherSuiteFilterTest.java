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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

public class CipherSuiteFilterTest {
    @Test
    public void supportedCipherSuiteFilterNonEmpty() {
        List<String> ciphers = Arrays.asList("foo", "goo", "noooo", "aaaa");
        List<String> defaultCiphers = Arrays.asList("FOO", "GOO");
        Set<String> supportedCiphers = Sets.newSet("FOO", "aaaa", "bbbbbb", "GOO", "goo");
        CipherSuiteFilter filter = SupportedCipherSuiteFilter.INSTANCE;
        String[] results = filter.filterCipherSuites(ciphers, defaultCiphers, supportedCiphers);
        assertEquals(2, results.length);
        assertEquals("goo", results[0]);
        assertEquals("aaaa", results[1]);
    }

    @Test
    public void supportedCipherSuiteFilterToEmpty() {
        List<String> ciphers = Arrays.asList("foo", "goo", "nooooo");
        List<String> defaultCiphers = Arrays.asList("FOO", "GOO");
        Set<String> supportedCiphers = Sets.newSet("FOO", "aaaa", "bbbbbb", "GOO");
        CipherSuiteFilter filter = SupportedCipherSuiteFilter.INSTANCE;
        String[] results = filter.filterCipherSuites(ciphers, defaultCiphers, supportedCiphers);
        assertEquals(0, results.length);
    }

    @Test
    public void supportedCipherSuiteFilterToDefault() {
        List<String> defaultCiphers = Arrays.asList("FOO", "GOO");
        Set<String> supportedCiphers = Sets.newSet("GOO", "FOO", "aaaa", "bbbbbb");
        CipherSuiteFilter filter = SupportedCipherSuiteFilter.INSTANCE;
        String[] results = filter.filterCipherSuites(null, defaultCiphers, supportedCiphers);
        assertEquals(2, results.length);
        assertEquals("FOO", results[0]);
        assertEquals("GOO", results[1]);
    }

    @Test
    public void defaulCipherSuiteNoFilter() {
        List<String> ciphers = Arrays.asList("foo", "goo", "nooooo");
        List<String> defaultCiphers = Arrays.asList("FOO", "GOO");
        Set<String> supportedCiphers = Sets.newSet("FOO", "aaaa", "bbbbbb", "GOO");
        CipherSuiteFilter filter = IdentityCipherSuiteFilter.INSTANCE;
        String[] results = filter.filterCipherSuites(ciphers, defaultCiphers, supportedCiphers);
        assertEquals(ciphers.size(), results.length);
        for (int i = 0; i < ciphers.size(); ++i) {
            assertEquals(ciphers.get(i), results[i]);
        }
    }

    @Test
    public void defaulCipherSuiteTakeDefault() {
        List<String> defaultCiphers = Arrays.asList("FOO", "GOO");
        Set<String> supportedCiphers = Sets.newSet("FOO", "aaaa", "bbbbbb", "GOO");
        CipherSuiteFilter filter = IdentityCipherSuiteFilter.INSTANCE;
        String[] results = filter.filterCipherSuites(null, defaultCiphers, supportedCiphers);
        assertEquals(defaultCiphers.size(), results.length);
        for (int i = 0; i < defaultCiphers.size(); ++i) {
            assertEquals(defaultCiphers.get(i), results[i]);
        }
    }
}
