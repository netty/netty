/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util;

import static org.junit.Assert.*;

import java.security.Permission;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public class DebugUtilTest {

    public void shouldReturnFalseIfPropertyIsNotSet() {
        assertFalse(DebugUtil.isDebugEnabled());
    }

    @Test
    public void shouldReturnTrueInDebugMode() {
        System.setProperty("io.netty.debug", "true");
        assertTrue(DebugUtil.isDebugEnabled());
    }

    @Test
    public void shouldReturnFalseInNonDebugMode() {
        System.setProperty("io.netty.debug", "false");
        assertFalse(DebugUtil.isDebugEnabled());
    }

    @Test
    public void shouldNotBombOutWhenSecurityManagerIsInAction() {
        System.setProperty("io.netty.debug", "true");
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPropertyAccess(String key) {
                throw new SecurityException();
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
                // Allow
            }

            @Override
            public void checkPermission(Permission perm) {
                // Allow
            }

        });
        try {
            assertFalse(DebugUtil.isDebugEnabled());
        } finally {
            System.setSecurityManager(null);
        }
    }

    @Before @After
    public void cleanup() {
        System.clearProperty("io.netty.debug");
    }
}
