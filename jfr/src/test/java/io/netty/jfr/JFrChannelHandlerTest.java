/*
 * Copyright 2021 The Netty Project
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
package io.netty.jfr;

import io.netty.channel.embedded.EmbeddedChannel;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies the correct functionality of the {@link JfrChannelHandler}.
 */
public class JFrChannelHandlerTest {

    @Test
    public void shouldRecordChannelConnect() throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("io.netty.channel.Connect").withoutThreshold();
            recording.start();
            EmbeddedChannel channel = new EmbeddedChannel(new JfrChannelHandler());
            channel.connect(new InetSocketAddress(80)).await();
            recording.stop();
            final Path tempFile = Files.createTempFile(JFrChannelHandlerTest.class.getSimpleName(), ".jfr");
            recording.dump(tempFile);

            final List<RecordedEvent> recordedEvents = RecordingFile.readAllEvents(tempFile);

            boolean hasConnectEvent = false;
            for (RecordedEvent event : recordedEvents) {
                if ("io.netty.channel.Connect".equals(event.getEventType().getName())) {
                    hasConnectEvent = true;
                    break;
                }
            }
            assertTrue("Expected a connect event", hasConnectEvent);
        }
    }
}
