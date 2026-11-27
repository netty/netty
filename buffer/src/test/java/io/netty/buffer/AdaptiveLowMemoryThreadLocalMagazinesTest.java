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

import io.netty.util.concurrent.FastThreadLocalThread;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The low memory flag and the property that disables thread-local magazines on low memory are read once into
 * static fields, so each configuration runs in its own JVM.
 */
@Timeout(60)
@EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
class AdaptiveLowMemoryThreadLocalMagazinesTest {
    private static final String RESULT_PREFIX = "chunkThreadLocal=";

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void lowMemoryThreadLocalMagazinesFollowProperty(boolean disableOnLowMemory, @TempDir Path tempDir)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add("-ea");
        command.add("-Dio.netty.allocator.lowMemory=true");
        command.add("-Dio.netty.allocator.disableThreadLocalMagazinesOnLowMemory=" + disableOnLowMemory);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(AllocateOnFastThreadLocalThread.class.getName());

        File log = tempDir.resolve("child.log").toFile();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start();
        try {
            // waitFor() is interruptible, unlike a read of the child's output, so @Timeout can end the test
            int exitCode = process.waitFor();
            String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertEquals(0, exitCode, output);
            // thread-local magazines are used unless low memory mode disables them
            assertEquals(RESULT_PREFIX + !disableOnLowMemory, lastLine(output), output);
        } finally {
            process.destroyForcibly();
        }
    }

    private static String lastLine(String output) {
        String[] lines = output.trim().split("\\r?\\n");
        return lines[lines.length - 1];
    }

    public static final class AllocateOnFastThreadLocalThread {
        @SuppressWarnings("Since15")
        public static void main(String[] args) throws Exception {
            final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator(true, true);
            final CompletableFuture<RecordedEvent> chunkEvent = new CompletableFuture<>();
            try (RecordingStream stream = new RecordingStream()) {
                stream.enable(AllocateChunkEvent.class);
                stream.onEvent(AllocateChunkEvent.NAME, chunkEvent::complete);
                stream.startAsync();

                FutureTask<Void> task = new FutureTask<>(() -> {
                    allocator.directBuffer(128).release();
                    return null;
                });
                new FastThreadLocalThread(task).start();
                task.get();

                System.out.println(RESULT_PREFIX + chunkEvent.get().getBoolean("threadLocal"));
            }
        }
    }
}
