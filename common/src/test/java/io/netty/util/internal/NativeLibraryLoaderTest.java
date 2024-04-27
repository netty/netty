/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.condition.OS.LINUX;

class NativeLibraryLoaderTest {

    private static final String OS_ARCH = System.getProperty("os.arch");
    private boolean is_x86_64() {
        return "x86_64".equals(OS_ARCH) || "amd64".equals(OS_ARCH);
    }

    @Test
    void testFileNotFound() {
        try {
            NativeLibraryLoader.load(UUID.randomUUID().toString(), NativeLibraryLoaderTest.class.getClassLoader());
            fail();
        } catch (UnsatisfiedLinkError error) {
            assertTrue(error.getCause() instanceof FileNotFoundException);
            verifySuppressedException(error, UnsatisfiedLinkError.class);
        }
    }

    @Test
    void testFileNotFoundWithNullClassLoader() {
        try {
            NativeLibraryLoader.load(UUID.randomUUID().toString(), null);
            fail();
        } catch (UnsatisfiedLinkError error) {
            assertTrue(error.getCause() instanceof FileNotFoundException);
            verifySuppressedException(error, ClassNotFoundException.class);
        }
    }

    @Test
    @EnabledOnOs(LINUX)
    @EnabledIf("is_x86_64")
    void testMultipleResourcesWithSameContentInTheClassLoader() throws MalformedURLException {
        URL url1 = new File("src/test/data/NativeLibraryLoader/1").toURI().toURL();
        URL url2 = new File("src/test/data/NativeLibraryLoader/2").toURI().toURL();
        final URLClassLoader loader = new URLClassLoader(new URL[] {url1, url2});
        final String resourceName = "test3";

        NativeLibraryLoader.load(resourceName, loader);
        assertTrue(true);
    }

    @Test
    @EnabledOnOs(LINUX)
    @EnabledIf("is_x86_64")
    void testMultipleResourcesInTheClassLoader() throws MalformedURLException {
        URL url1 = new File("src/test/data/NativeLibraryLoader/1").toURI().toURL();
        URL url2 = new File("src/test/data/NativeLibraryLoader/2").toURI().toURL();
        final URLClassLoader loader = new URLClassLoader(new URL[] {url1, url2});
        final String resourceName = "test1";

        Exception ise = assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                NativeLibraryLoader.load(resourceName, loader);
            }
        });
        assertTrue(ise.getMessage()
                    .contains("Multiple resources found for 'META-INF/native/lib" + resourceName + ".so'"));
    }

    @Test
    @EnabledOnOs(LINUX)
    @EnabledIf("is_x86_64")
    void testSingleResourceInTheClassLoader() throws MalformedURLException {
        URL url1 = new File("src/test/data/NativeLibraryLoader/1").toURI().toURL();
        URL url2 = new File("src/test/data/NativeLibraryLoader/2").toURI().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[] {url1, url2});
        String resourceName = "test2";

        NativeLibraryLoader.load(resourceName, loader);
        assertTrue(true);
    }

    private static void verifySuppressedException(UnsatisfiedLinkError error,
            Class<?> expectedSuppressedExceptionClass) {
        try {
            Throwable[] suppressed = error.getCause().getSuppressed();
            assertTrue(suppressed.length == 1);
            assertTrue(suppressed[0] instanceof UnsatisfiedLinkError);
            suppressed = (suppressed[0]).getSuppressed();
            assertTrue(suppressed.length == 1);
            assertTrue(expectedSuppressedExceptionClass.isInstance(suppressed[0]));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
