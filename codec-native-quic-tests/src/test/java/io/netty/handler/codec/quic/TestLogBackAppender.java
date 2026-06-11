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
package io.netty.handler.codec.quic;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TestLogBackAppender extends AppenderBase<ILoggingEvent> {
    private static final List<String> logs = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        logs.add(iLoggingEvent.getFormattedMessage());
    }

    public static List<String> getLogs() {
        return logs;
    }

    public static void clearLogs() {
        logs.clear();
    }
}
