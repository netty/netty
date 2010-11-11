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
package org.jboss.netty.util;

import static org.junit.Assert.*;

import java.security.Permission;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class DebugUtilTest {

    public void shouldReturnFalseIfPropertyIsNotSet() {
        assertFalse(DebugUtil.isDebugEnabled());
    }

    @Test
    public void shouldReturnTrueInDebugMode() {
        System.setProperty("org.jboss.netty.debug", "true");
        assertTrue(DebugUtil.isDebugEnabled());
    }

    @Test
    public void shouldReturnFalseInNonDebugMode() {
        System.setProperty("org.jboss.netty.debug", "false");
        assertFalse(DebugUtil.isDebugEnabled());
    }

    @Test
    public void shouldNotBombOutWhenSecurityManagerIsInAction() {
        System.setProperty("org.jboss.netty.debug", "true");
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
        System.clearProperty("org.jboss.netty.debug");
    }
}
