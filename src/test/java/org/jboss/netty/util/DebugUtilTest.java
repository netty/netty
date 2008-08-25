/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.util;

import static org.junit.Assert.*;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
            public void checkAccept(String host, int port) {
                // Allow
            }

            @Override
            public void checkAccess(Thread t) {
                // Allow
            }

            @Override
            public void checkAccess(ThreadGroup g) {
                // Allow
            }

            @Override
            public void checkAwtEventQueueAccess() {
                // Allow
            }

            @Override
            public void checkConnect(String host, int port, Object context) {
                // Allow
            }

            @Override
            public void checkConnect(String host, int port) {
                // Allow
            }

            @Override
            public void checkCreateClassLoader() {
                // Allow
            }

            @Override
            public void checkDelete(String file) {
                // Allow
            }

            @Override
            public void checkExec(String cmd) {
                // Allow
            }

            @Override
            public void checkExit(int status) {
                // Allow
            }

            @Override
            public void checkLink(String lib) {
                // Allow
            }

            @Override
            public void checkListen(int port) {
                // Allow
            }

            @Override
            public void checkMemberAccess(Class<?> clazz, int which) {
                // Allow
            }

            @Override
            public void checkMulticast(InetAddress maddr, byte ttl) {
                // Allow
            }

            @Override
            public void checkMulticast(InetAddress maddr) {
                // Allow
            }

            @Override
            public void checkPackageAccess(String pkg) {
                // Allow
            }

            @Override
            public void checkPackageDefinition(String pkg) {
                // Allow
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
                // Allow
            }

            @Override
            public void checkPermission(Permission perm) {
                // Allow
            }

            @Override
            public void checkPrintJobAccess() {
                // Allow
            }

            @Override
            public void checkPropertiesAccess() {
                // Allow
            }

            @Override
            public void checkRead(FileDescriptor fd) {
                // Allow
            }

            @Override
            public void checkRead(String file, Object context) {
                // Allow
            }

            @Override
            public void checkRead(String file) {
                // Allow
            }

            @Override
            public void checkSecurityAccess(String target) {
                // Allow
            }

            @Override
            public void checkSetFactory() {
                // Allow
            }

            @Override
            public void checkSystemClipboardAccess() {
                // Allow
            }

            @Override
            public boolean checkTopLevelWindow(Object window) {
                return true;
            }

            @Override
            public void checkWrite(FileDescriptor fd) {
                // Allow
            }

            @Override
            public void checkWrite(String file) {
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
