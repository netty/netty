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

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;

public class IdentityCipherSuiteFilterTest {

    @Test
    public void regularInstanceDefaultsToDefaultCiphers() {
        List<String> defaultCiphers = Arrays.asList("FOO", "BAR");
        Set<String> supportedCiphers = new HashSet<String>(Arrays.asList("BAZ", "QIX"));
        String[] filtered = IdentityCipherSuiteFilter.INSTANCE
                .filterCipherSuites(null, defaultCiphers, supportedCiphers);
        assertArrayEquals(defaultCiphers.toArray(), filtered);
    }

    @Test
    public void alternativeInstanceDefaultsToSupportedCiphers() {
        List<String> defaultCiphers = Arrays.asList("FOO", "BAR");
        Set<String> supportedCiphers = new HashSet<String>(Arrays.asList("BAZ", "QIX"));
        String[] filtered = IdentityCipherSuiteFilter.INSTANCE_DEFAULTING_TO_SUPPORTED_CIPHERS
                .filterCipherSuites(null, defaultCiphers, supportedCiphers);
        assertArrayEquals(supportedCiphers.toArray(), filtered);
    }
}
