/*
 * Copyright 2017 The Netty Project
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

import java.lang.reflect.Method;
import java.io.FileNotFoundException;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NativeLibraryLoaderTest {

    private static final Method getSupressedMethod = getGetSuppressed();

    @Test
    public void testFileNotFound() {
        try {
            NativeLibraryLoader.load(UUID.randomUUID().toString(), NativeLibraryLoaderTest.class.getClassLoader());
            fail();
        } catch (UnsatisfiedLinkError error) {
            assertTrue(error.getCause() instanceof FileNotFoundException);
            if (getSupressedMethod != null) {
                verifySuppressedException(error, UnsatisfiedLinkError.class);
            }
        }
    }

    @Test
    public void testFileNotFoundWithNullClassLoader() {
        try {
            NativeLibraryLoader.load(UUID.randomUUID().toString(), null);
            fail();
        } catch (UnsatisfiedLinkError error) {
            assertTrue(error.getCause() instanceof FileNotFoundException);
            if (getSupressedMethod != null) {
                verifySuppressedException(error, ClassNotFoundException.class);
            }
        }
    }

    private static void verifySuppressedException(UnsatisfiedLinkError error,
            Class<?> expectedSuppressedExceptionClass) {
        try {
            Throwable[] suppressed = (Throwable[]) getSupressedMethod.invoke(error.getCause());
            assertTrue(suppressed.length == 1);
            assertTrue(suppressed[0] instanceof UnsatisfiedLinkError);
            suppressed = (Throwable[]) getSupressedMethod.invoke(suppressed[0]);
            assertTrue(suppressed.length == 1);
            assertTrue(expectedSuppressedExceptionClass.isInstance(suppressed[0]));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method getGetSuppressed() {
        if (PlatformDependent.javaVersion() < 7) {
            return null;
        }
        try {
            return Throwable.class.getDeclaredMethod("getSuppressed");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
