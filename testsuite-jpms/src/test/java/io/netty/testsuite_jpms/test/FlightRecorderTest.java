/*
 * Copyright 2024 The Netty Project
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
package io.netty.testsuite_jpms.test;

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.util.internal.PlatformDependent;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This ensures that Netty JFR support works in a modular runtime.
 */
public class FlightRecorderTest {

    @Test
    public void testJfrEnabled() {
        assertTrue(PlatformDependent.isJfrEnabled());
    }

    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    @Timeout(10)
    @Test
    public void testAdaptiveAllocatorEvent()  throws Exception {

        ClassLoader cl = ClassLoader.getSystemClassLoader();
        Class<?> recordingStreamClass = cl.loadClass("jdk.jfr.consumer.RecordingStream");
        Constructor<?> ctor = recordingStreamClass.getConstructor();
        Method enableMethod = recordingStreamClass.getMethod("enable", String.class);
        Method onEventMethod = recordingStreamClass.getMethod("onEvent", String.class, Consumer.class);
        Method startAsyncMethod = recordingStreamClass.getMethod("startAsync");
        Object recordingStream = ctor.newInstance();

        try (AutoCloseable c = (AutoCloseable) recordingStream) {
            CompletableFuture<RecordedEvent> allocateFuture = new CompletableFuture<>();
            Consumer<RecordedEvent> complete = allocateFuture::complete;
            enableMethod.invoke(recordingStream, "io.netty.AllocateChunk");
            onEventMethod.invoke(recordingStream, "io.netty.AllocateChunk", complete);
            startAsyncMethod.invoke(recordingStream);

            AdaptiveByteBufAllocator alloc = new AdaptiveByteBufAllocator(true, false);
            alloc.directBuffer(128).release();

            RecordedEvent allocate = allocateFuture.get();
            assertTrue(allocate.getBoolean("pooled"));
            assertFalse(allocate.getBoolean("threadLocal"));
            assertTrue(allocate.getBoolean("direct"));
        }
    }
}
