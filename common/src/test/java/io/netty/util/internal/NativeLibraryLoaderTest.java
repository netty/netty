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

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NativeLibraryLoaderTest {

    @Test
    public void testFileNotFound() {
        try {
            NativeLibraryLoader.load(UUID.randomUUID().toString(), NativeLibraryLoaderTest.class.getClassLoader());
            fail();
        } catch (UnsatisfiedLinkError error) {
            assertTrue(error.getCause() instanceof FileNotFoundException);
            if (PlatformDependent.javaVersion() >= 7) {
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
            if (PlatformDependent.javaVersion() >= 7) {
                verifySuppressedException(error, ClassNotFoundException.class);
            }
        }
    }

    @SuppressJava6Requirement(reason = "uses Java 7+ Throwable#getSuppressed but is guarded by version checks")
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

    @Test
    public void testPatchingId() throws IOException {
        testPatchingId0(true, false);
    }

    @Test
    public void testPatchingIdWithOsArch() throws IOException {
        testPatchingId0(true, true);
    }

    @Test
    public void testPatchingIdNotMatch() throws IOException {
        testPatchingId0(false, false);
    }

    @Test
    public void testPatchingIdWithOsArchNotMatch() throws IOException {
        testPatchingId0(false, true);
    }

    private static void testPatchingId0(boolean match, boolean withOsArch) throws IOException {
        byte[] bytes = new byte[1024];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        byte[] idBytes = ("/workspace/netty-tcnative/boringssl-static/target/" +
                "native-build/target/lib/libnetty_tcnative-2.0.20.Final.jnilib").getBytes(CharsetUtil.UTF_8);

        String originalName;
        if (match) {
            originalName = "netty-tcnative";
        } else {
            originalName = "nonexist_tcnative";
        }
        String name = "shaded_" + originalName;
        if (withOsArch) {
            name += "_osx_x86_64";
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes, 0, bytes.length);
        out.write(idBytes, 0, idBytes.length);
        out.write(bytes, 0 , bytes.length);

        out.flush();
        byte[] inBytes = out.toByteArray();
        out.close();

        InputStream inputStream = new ByteArrayInputStream(inBytes);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            assertEquals(match,
                    NativeLibraryLoader.patchShadedLibraryId(inputStream, outputStream, originalName, name));

            outputStream.flush();
            byte[] outputBytes = outputStream.toByteArray();
            assertArrayEquals(bytes, Arrays.copyOfRange(outputBytes, 0, bytes.length));
            byte[] patchedId = Arrays.copyOfRange(outputBytes, bytes.length, bytes.length + idBytes.length);
            assertEquals(!match, Arrays.equals(idBytes, patchedId));
            assertArrayEquals(bytes,
                    Arrays.copyOfRange(outputBytes, bytes.length + idBytes.length, outputBytes.length));
            assertEquals(inBytes.length, outputBytes.length);
        } finally {
            inputStream.close();
            outputStream.close();
        }
    }
}
