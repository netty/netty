/*
 * Copyright 2026 The Netty Project
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
package io.netty.buffer;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Netty registers its JFR event classes only once a Flight Recorder exists, because the first registration sets up
 * JFR's metadata repository, which is expensive. Each test runs in a new JVM: a recorder cannot be shut down, and
 * other tests start recordings.
 */
@EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
public class JfrEventRegistrationTest {
    private static final String METADATA_REPOSITORY = "jdk.jfr.internal.MetadataRepository";

    @TempDir
    Path tempDir;

    @Test
    public void recordingStartedAfterFirstAllocation() throws Exception {
        List<String> loadedClasses = runForkedJvm(ForkedJvm.RECORD_AFTER_ALLOCATION);

        int recordingStart = indexOf(loadedClasses, Recorder.class.getName());
        assertTrue(recordingStart >= 0, "The forked JVM did not start a recording");
        int metadataRepository = indexOf(loadedClasses, METADATA_REPOSITORY);
        assertTrue(metadataRepository < 0 || metadataRepository > recordingStart,
                "JFR set up its metadata repository before a recording was started");
    }

    @Test
    public void recordingStartedBeforeNettyIsInitialized() throws Exception {
        runForkedJvm(ForkedJvm.RECORD_BEFORE_ALLOCATION);
    }

    @Test
    public void disabledDoesNotLoadJfr() throws Exception {
        List<String> loadedClasses = runForkedJvm(ForkedJvm.ALLOCATE, "-Dio.netty.jfr.enabled=false");

        for (String loadedClass : loadedClasses) {
            assertFalse(loadedClass.startsWith("jdk.jfr."), loadedClass);
        }
    }

    /**
     * Runs {@link ForkedJvm} and returns the classes it loaded, in order.
     */
    private List<String> runForkedJvm(String mode, String... jvmArgs) throws Exception {
        File out = tempDir.resolve(mode + ".out").toFile();
        File err = tempDir.resolve(mode + ".err").toFile();
        List<String> command = new ArrayList<>();
        command.add(new File(new File(System.getProperty("java.home"), "bin"), "java").getPath());
        // One line per loaded class: "<class name> source: <source>".
        command.add("-Xlog:class+load=info:stdout:none");
        Collections.addAll(command, jvmArgs);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ForkedJvm.class.getName());
        command.add(mode);

        Process process = new ProcessBuilder(command).redirectOutput(out).redirectError(err).start();
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            fail("The forked JVM did not exit in time:\n" + read(err));
        }
        assertEquals(0, process.exitValue(), read(err));
        return Files.readAllLines(out.toPath(), StandardCharsets.ISO_8859_1);
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
    }

    private static int indexOf(List<String> loadedClasses, String className) {
        for (int i = 0; i < loadedClasses.size(); i++) {
            if (loadedClasses.get(i).startsWith(className + " source: ")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The main class of the forked JVM. It does not use {@code jdk.jfr} itself, see {@link Recorder}.
     */
    public static final class ForkedJvm {
        static final String ALLOCATE = "allocate";
        static final String RECORD_AFTER_ALLOCATION = "record-after-allocation";
        static final String RECORD_BEFORE_ALLOCATION = "record-before-allocation";

        private ForkedJvm() {
        }

        public static void main(String[] args) throws Exception {
            String mode = args[0];
            if (ALLOCATE.equals(mode) || RECORD_AFTER_ALLOCATION.equals(mode)) {
                allocate();
            }
            if (RECORD_AFTER_ALLOCATION.equals(mode) || RECORD_BEFORE_ALLOCATION.equals(mode)) {
                Recorder.expectAllocateChunkEvent();
            }
        }

        static void allocate() {
            ByteBufAllocator[] allocators = {
                    new AdaptiveByteBufAllocator(true, false),
                    new PooledByteBufAllocator(true)
            };
            for (ByteBufAllocator allocator : allocators) {
                ByteBuf buf = allocator.directBuffer(128);
                buf.capacity(64 * 1024);
                buf.release();
            }
        }
    }

    /**
     * Uses {@code jdk.jfr}, so that the forked JVM loads it only once this class is loaded.
     */
    @SuppressWarnings("Since15")
    static final class Recorder {
        private Recorder() {
        }

        static void expectAllocateChunkEvent() throws Exception {
            try (RecordingStream stream = new RecordingStream()) {
                CompletableFuture<RecordedEvent> event = new CompletableFuture<>();
                stream.enable(AllocateChunkEvent.NAME);
                stream.onEvent(AllocateChunkEvent.NAME, event::complete);
                stream.startAsync();

                ForkedJvm.allocate();

                event.get(1, TimeUnit.MINUTES);
            }
        }
    }
}
