/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.netty.util.internal;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RuntimeJvmArgsTest {

    private static final String ARG_PREFIX = "-XX:MaxDirectMemorySize=";

    @Test
    public void testParseSizeWithoutSuffix() {
        assertEquals(12345L, RuntimeJvmArgs.parseSize(ARG_PREFIX + "12345", ARG_PREFIX.length()));
    }

    @Test
    public void testParseSizeWithKiloSuffix() {
        assertEquals(2048L, RuntimeJvmArgs.parseSize(ARG_PREFIX + "2k", ARG_PREFIX.length()));
        assertEquals(2048L, RuntimeJvmArgs.parseSize(ARG_PREFIX + "2K", ARG_PREFIX.length()));
    }

    @Test
    public void testParseSizeWithMegaSuffix() {
        assertEquals(8L * 1024 * 1024, RuntimeJvmArgs.parseSize(ARG_PREFIX + "8m", ARG_PREFIX.length()));
        assertEquals(8L * 1024 * 1024, RuntimeJvmArgs.parseSize(ARG_PREFIX + "8M", ARG_PREFIX.length()));
    }

    @Test
    public void testParseSizeWithGigaSuffix() {
        assertEquals(1024L * 1024 * 1024, RuntimeJvmArgs.parseSize(ARG_PREFIX + "1g", ARG_PREFIX.length()));
        assertEquals(1024L * 1024 * 1024, RuntimeJvmArgs.parseSize(ARG_PREFIX + "1G", ARG_PREFIX.length()));
    }

    @Test
    public void testParseSizeWithEmptyValueReturnsNegativeOne() {
        assertEquals(-1L, RuntimeJvmArgs.parseSize(ARG_PREFIX, ARG_PREFIX.length()));
    }

    @Test
    public void testParseSizeWithWhitespaceOnlyValueReturnsNegativeOne() {
        assertEquals(-1L, RuntimeJvmArgs.parseSize(ARG_PREFIX + "   ", ARG_PREFIX.length()));
    }

    @Test
    public void testParseSizeWithInvalidNumberThrows() {
        assertThrows(NumberFormatException.class,
                () -> RuntimeJvmArgs.parseSize(ARG_PREFIX + "abc", ARG_PREFIX.length()));
    }

    @Test
    public void testParseSizeWithUnsupportedSuffixThrows() {
        assertThrows(NumberFormatException.class,
                () -> RuntimeJvmArgs.parseSize(ARG_PREFIX + "5t", ARG_PREFIX.length()));
    }

    @Test
    public void testParseMaxDirectMemorySizeFallsBackWhenArgAbsent() {
        List<String> vmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : vmArgs) {
            if (arg.startsWith(ARG_PREFIX)) {
                // The running JVM was launched with the flag; skip as the outcome is environment-dependent.
                return;
            }
        }
        assertEquals(1234L, RuntimeJvmArgs.parseMaxDirectMemorySize(1234L));
    }
}
