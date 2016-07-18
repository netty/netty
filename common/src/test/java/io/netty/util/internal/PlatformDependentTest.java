/*
 * Copyright 2015 The Netty Project
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
package io.netty.util.internal;

import org.junit.Test;

import java.security.Permission;

import static org.junit.Assert.assertEquals;

public class PlatformDependentTest {

    @Test
    public void testMajorVersionFromJavaSpecificationVersion() {
        final SecurityManager current = System.getSecurityManager();

        try {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPropertyAccess(String key) {
                    if (key.equals("java.specification.version")) {
                        // deny
                        throw new SecurityException(key);
                    }
                }

                // so we can restore the security manager
                @Override
                public void checkPermission(Permission perm) {
                }
            });

            assertEquals(6, PlatformDependent.majorVersionFromJavaSpecificationVersion());
        } finally {
            System.setSecurityManager(current);
        }
    }

    @Test
    public void testMajorVersion() {
        assertEquals(6, PlatformDependent.majorVersion("1.6"));
        assertEquals(7, PlatformDependent.majorVersion("1.7"));
        assertEquals(8, PlatformDependent.majorVersion("1.8"));
        assertEquals(8, PlatformDependent.majorVersion("8"));
        assertEquals(9, PlatformDependent.majorVersion("1.9")); // early version of JDK 9 before Project Verona
        assertEquals(9, PlatformDependent.majorVersion("9"));
    }
}
