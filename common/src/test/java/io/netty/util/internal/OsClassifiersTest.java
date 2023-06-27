/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.util.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OsClassifiersTest {
    private static final String OS_CLASSIFIERS_PROPERTY = "io.netty.osClassifiers";

    private Properties systemProperties;

    @BeforeEach
    void setUp() {
        systemProperties = System.getProperties();
    }

    @AfterEach
    void tearDown() {
        systemProperties.remove(OS_CLASSIFIERS_PROPERTY);
    }

    @Test
    void testOsClassifiersPropertyAbsent() {
        Set<String> allowed = new HashSet<String>(Arrays.asList("fedora", "suse", "arch"));
        Set<String> available = new LinkedHashSet<String>(2);
        boolean added = PlatformDependent.addPropertyOsClassifiers(allowed, available);
        assertFalse(added);
        assertTrue(available.isEmpty());
    }

    @Test
    void testOsClassifiersPropertyEmpty() {
        // empty property -Dio.netty.osClassifiers
        systemProperties.setProperty(OS_CLASSIFIERS_PROPERTY, "");
        Set<String> allowed = Collections.singleton("fedora");
        Set<String> available = new LinkedHashSet<String>(2);
        boolean added = PlatformDependent.addPropertyOsClassifiers(allowed, available);
        assertTrue(added);
        assertTrue(available.isEmpty());
    }

    @Test
    void testOsClassifiersPropertyNotEmptyNoClassifiers() {
        // ID
        systemProperties.setProperty(OS_CLASSIFIERS_PROPERTY, ",");
        final Set<String> allowed = new HashSet<String>(Arrays.asList("fedora", "suse", "arch"));
        final Set<String> available = new LinkedHashSet<String>(2);
        Assertions.assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                PlatformDependent.addPropertyOsClassifiers(allowed, available);
            }
        });
    }

    @Test
    void testOsClassifiersPropertySingle() {
        // ID
        systemProperties.setProperty(OS_CLASSIFIERS_PROPERTY, "fedora");
        Set<String> allowed = Collections.singleton("fedora");
        Set<String> available = new LinkedHashSet<String>(2);
        boolean added = PlatformDependent.addPropertyOsClassifiers(allowed, available);
        assertTrue(added);
        assertEquals(1, available.size());
        assertEquals("fedora", available.iterator().next());
    }

    @Test
    void testOsClassifiersPropertyPair() {
        // ID, ID_LIKE
        systemProperties.setProperty(OS_CLASSIFIERS_PROPERTY, "manjaro,arch");
        Set<String> allowed = new HashSet<String>(Arrays.asList("fedora", "suse", "arch"));
        Set<String> available = new LinkedHashSet<String>(2);
        boolean added = PlatformDependent.addPropertyOsClassifiers(allowed, available);
        assertTrue(added);
        assertEquals(1, available.size());
        assertEquals("arch", available.iterator().next());
    }

    @Test
    void testOsClassifiersPropertyExcessive() {
        // ID, ID_LIKE, excessive
        systemProperties.setProperty(OS_CLASSIFIERS_PROPERTY, "manjaro,arch,slackware");
        final Set<String> allowed = new HashSet<String>(Arrays.asList("fedora", "suse", "arch"));
        final Set<String> available = new LinkedHashSet<String>(2);
        Assertions.assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                PlatformDependent.addPropertyOsClassifiers(allowed, available);
            }
        });
    }
}
