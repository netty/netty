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
package io.netty.testsuite_jpms.test.jfr;

import io.netty.buffer.AllocateChunkEvent;
import io.netty.util.internal.PlatformDependent;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test relies on {@link RecordingStream} that is only available since JDK 14.
 *
 * It ensures that Netty JFR supports works in a modular runtime.
 */
public class FlightRecorderTest {

    private Recording recording;
    private RecordingStream stream;
    private Deque<RecordedEvent> events;
    private CountDownLatch startRecordingLatch;
    private CountDownLatch stopRecordingLatch;

    @AfterEach
    public void after() {
        if (recording != null) {
            recording.stop();
            recording.close();
            recording = null;
        }
        if (stream != null) {
            stream.stop();
            stream = null;
        }
    }

    @Test
    public void testJfrEnabled() {
        assertTrue(PlatformDependent.isJfrEnabled());
    }

    @Test
    public void testAdaptiveAllocatorEvent()  throws InterruptedException {
        startRecording("AllocateChunkEvent");
        AllocateChunkEvent expected = new AllocateChunkEvent();
        expected.direct = true;
        expected.capacity = 16;
        expected.commit();
        Deque<RecordedEvent> events = stopRecord();
        assertEquals(1, events.size());
        boolean found = false;
        for  (RecordedEvent event; !found && (event = events.poll()) != null;) {
            if (event.getEventType().getName().equals("AllocateChunkEvent") && event.getBoolean("direct")
                    && event.getInt("capacity") == 16) {
                found = true;
            }
        }
        assertTrue(found);
    }

    private void startRecording(String... eventNames) throws InterruptedException {
        startRecordingLatch = new CountDownLatch(1);
        stopRecordingLatch = new CountDownLatch(1);
        events = new ArrayDeque<>();
        stream = new RecordingStream();
        stream.setReuse(false);
        stream.enable(RecordingStartedEvent.EVENT_NAME);
        stream.enable(RecordingStoppedEvent.EVENT_NAME);
        for (String eventName : eventNames) {
            stream.enable(eventName);
        }
        stream.onEvent(event -> {
            if (event.getEventType().getName().equals(RecordingStartedEvent.EVENT_NAME)) {
                startRecordingLatch.countDown();
            } else if (event.getEventType().getName().equals(RecordingStoppedEvent.EVENT_NAME)) {
                stopRecordingLatch.countDown();
            } else {
                events.add(event);
            }
        });
        stream.startAsync();
        recording = new Recording();
        recording.enable(RecordingStartedEvent.EVENT_NAME);
        recording.enable(RecordingStoppedEvent.EVENT_NAME);
        for (String eventName : eventNames) {
            recording.enable(eventName);
        }
        recording.start();
        RecordingStartedEvent event = new RecordingStartedEvent();
        event.begin();
        event.commit();
        assertTrue(startRecordingLatch.await(10, TimeUnit.SECONDS));
    }

    private Deque<RecordedEvent> stopRecord() throws InterruptedException {
        RecordingStoppedEvent event = new RecordingStoppedEvent();
        event.begin();
        event.commit();
        assertTrue(stopRecordingLatch.await(10, TimeUnit.SECONDS));
        recording.stop();
        recording.close();
        recording = null;
        stream.stop();
        stream = null;
        return events;
    }
}
