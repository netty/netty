/*
 * Copyright 2025 The Netty Project
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

import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("Since15")
public class JfrEventSafeTest {
    @Test
    public void test() {
        // This code should work even on java 8. Other details are tested in JfrEventTest.
        if (PlatformDependent.isJfrEnabled()) {
            MyEvent event = new MyEvent();
            event.foo = "bar";
            event.commit();
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    public void simple() throws Throwable {
        try (RecordingStream stream = new RecordingStream()) {
            stream.enable(MyEvent.class.getName());
            CompletableFuture<String> result = new CompletableFuture<>();
            stream.onEvent(MyEvent.class.getName(), e -> result.complete(e.getString("foo")));
            stream.startAsync();

            MyEvent event = new MyEvent();
            event.foo = "bar";
            event.commit();

            assertEquals("bar", result.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_17) // RecordingStream
    public void enableDefaults() throws Throwable {
        try (RecordingStream stream = new RecordingStream()) {
            CompletableFuture<String> result = new CompletableFuture<>();
            stream.onEvent(DisabledEvent.class.getName(),
                    e -> result.completeExceptionally(new Exception("Event mistakenly fired")));
            stream.onEvent(MyEvent.class.getName(),
                    e -> result.complete(e.getString("foo")));
            stream.startAsync();

            DisabledEvent disabled = new DisabledEvent();
            disabled.foo = "baz";
            disabled.commit();

            MyEvent event = new MyEvent();
            event.foo = "bar";
            event.commit();

            assertEquals("bar", result.get(10, TimeUnit.SECONDS));
        }
    }

    static final class MyEvent extends Event {
        String foo;
    }

    @Enabled(false)
    static final class DisabledEvent extends Event {
        String foo;
    }
}
