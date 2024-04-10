/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.logging;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.WriteBufferWaterMark;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentMatcher;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.util.internal.StringUtil.NEWLINE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies the correct functionality of the {@link LoggingHandler}.
 */
public class LoggingHandlerTest {
    private static final class TestLogger implements Logger {
        public static final String NAME = "TestLogger";
        private final ArrayList<String> lines = new ArrayList<>();

        public void append(String format, Object... args) {
            synchronized (lines) {
                FormattingTuple formattingTuple = MessageFormatter.arrayFormat(format, args);
                lines.add(formattingTuple.getMessage());
            }
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public void trace(String msg) {
            append(msg);
        }

        @Override
        public void trace(String format, Object arg) {
            append(format, arg);
        }

        @Override
        public void trace(String format, Object first, Object second) {
            append(format, first, second);
        }

        @Override
        public void trace(String format, Object... arguments) {
            append(format, arguments);
        }

        @Override
        public void trace(String msg, Throwable t) {
            append(msg, t);
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return true;
        }

        @Override
        public void trace(Marker marker, String msg) {
            trace(msg);
        }

        @Override
        public void trace(Marker marker, String format, Object arg) {
            trace(format, arg);
        }

        @Override
        public void trace(Marker marker, String format, Object first, Object second) {
            trace(format, first, second);
        }

        @Override
        public void trace(Marker marker, String format, Object... argArray) {
            trace(format, argArray);
        }

        @Override
        public void trace(Marker marker, String msg, Throwable t) {
            trace(msg, t);
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(String msg) {
            append(msg);
        }

        @Override
        public void debug(String format, Object arg) {
            append(format, arg);
        }

        @Override
        public void debug(String format, Object first, Object second) {
            append(format, first, second);
        }

        @Override
        public void debug(String format, Object... arguments) {
            append(format, arguments);
        }

        @Override
        public void debug(String msg, Throwable t) {
            append(msg, t);
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return true;
        }

        @Override
        public void debug(Marker marker, String msg) {
            debug(msg);
        }

        @Override
        public void debug(Marker marker, String format, Object arg) {
            debug(format, arg);
        }

        @Override
        public void debug(Marker marker, String format, Object first, Object second) {
            debug(format, first, second);
        }

        @Override
        public void debug(Marker marker, String format, Object... arguments) {
            debug(format, arguments);
        }

        @Override
        public void debug(Marker marker, String msg, Throwable t) {
            debug(msg, t);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(String msg) {
            append(msg);
        }

        @Override
        public void info(String format, Object arg) {
            append(format, arg);
        }

        @Override
        public void info(String format, Object first, Object second) {
            append(format, first, second);
        }

        @Override
        public void info(String format, Object... arguments) {
            append(format, arguments);
        }

        @Override
        public void info(String msg, Throwable t) {
            append(msg, t);
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return true;
        }

        @Override
        public void info(Marker marker, String msg) {
            info(msg);
        }

        @Override
        public void info(Marker marker, String format, Object arg) {
            info(format, arg);
        }

        @Override
        public void info(Marker marker, String format, Object first, Object second) {
            info(format, first, second);
        }

        @Override
        public void info(Marker marker, String format, Object... arguments) {
            info(format, arguments);
        }

        @Override
        public void info(Marker marker, String msg, Throwable t) {
            info(msg, t);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(String msg) {
            append(msg);
        }

        @Override
        public void warn(String format, Object arg) {
            append(format, arg);
        }

        @Override
        public void warn(String format, Object... arguments) {
            append(format, arguments);
        }

        @Override
        public void warn(String format, Object first, Object second) {
            append(format, first, second);
        }

        @Override
        public void warn(String msg, Throwable t) {
            append(msg, t);
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return true;
        }

        @Override
        public void warn(Marker marker, String msg) {
            warn(msg);
        }

        @Override
        public void warn(Marker marker, String format, Object arg) {
            warn(format, arg);
        }

        @Override
        public void warn(Marker marker, String format, Object first, Object second) {
            warn(format, first, second);
        }

        @Override
        public void warn(Marker marker, String format, Object... arguments) {
            warn(format, arguments);
        }

        @Override
        public void warn(Marker marker, String msg, Throwable t) {
            warn(msg, t);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(String msg) {
            append(msg);
        }

        @Override
        public void error(String format, Object arg) {
            append(format, arg);
        }

        @Override
        public void error(String format, Object first, Object second) {
            append(format, first, second);
        }

        @Override
        public void error(String format, Object... arguments) {
            append(format, arguments);
        }

        @Override
        public void error(String msg, Throwable t) {
            append(msg, t);
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return true;
        }

        @Override
        public void error(Marker marker, String msg) {
            error(msg);
        }

        @Override
        public void error(Marker marker, String format, Object arg) {
            error(format, arg);
        }

        @Override
        public void error(Marker marker, String format, Object first, Object second) {
            error(format, first, second);
        }

        @Override
        public void error(Marker marker, String format, Object... arguments) {
            error(format, arguments);
        }

        @Override
        public void error(Marker marker, String msg, Throwable t) {
            error(msg, t);
        }

        public void verify(ArgumentMatcher<String> matcher) {
            synchronized (lines) {
                boolean found = false;
                for (String line : lines) {
                    if (matcher.matches(line)) {
                        found = true;
                    }
                }
                if (!found) {
                    StringBuilder sb = new StringBuilder("Wanted\n\t").append(matcher).append("\nbut got");
                    if (lines.isEmpty()) {
                        sb.append(" nothing.");
                    } else {
                        for (String line : lines) {
                            sb.append("\n\t").append(line);
                        }
                    }
                    fail(sb.toString());
                }
            }
        }

        public void verify(ArgumentMatcher<String> matcher, int times) {
            synchronized (lines) {
                int count = 0;
                for (String line : lines) {
                    if (matcher.matches(line)) {
                        count++;
                    }
                }
                if (count != times) {
                    StringBuilder sb = new StringBuilder("Wanted\n\t").append(matcher)
                            .append('\n').append(times).append(" times, but got ").append(count).append(" times.");
                    fail(sb.toString());
                }
            }
        }
    }

    /**
     * Custom Logger which gets used to match on log messages.
     */
    private static final TestLogger appender = new TestLogger();

    @Test
    public void shouldNotAcceptNullLogLevel() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                LogLevel level = null;
                newLoggingHandler(level);
            }
        });
    }

    @Test
    public void shouldApplyCustomLogLevel() {
        LoggingHandler handler = newLoggingHandler(LogLevel.INFO);
        assertEquals(LogLevel.INFO, handler.level());
    }

    @Test
    public void shouldLogChannelActive() {
        new EmbeddedChannel(newLoggingHandler());
        appender.verify(new RegexLogMatcher(".+ACTIVE"));
    }

    @Test
    public void shouldLogChannelWritabilityChanged() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        // this is used to switch the channel to become unwritable
        channel.setOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(5, 10));
        channel.write("hello");

        // This is expected to be called 3 times:
        // - Mark the channel unwritable when schedule the write on the EventLoop.
        // - Mark writable when dequeue task
        // - Mark unwritable when the write is actual be fired through the pipeline and hit the ChannelOutboundBuffer.
        appender.verify(new RegexLogMatcher(".+WRITABILITY CHANGED$"), 3);
    }

    @Test
    public void shouldLogChannelRegistered() {
        new EmbeddedChannel(newLoggingHandler());
        appender.verify(new RegexLogMatcher(".+REGISTERED$"));
    }

    @Test
    public void shouldLogChannelClose() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.close().asStage().await();
        appender.verify(new RegexLogMatcher(".+CLOSE$"));
    }

    @Test
    public void shouldLogChannelConnect() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.connect(new InetSocketAddress(80)).asStage().await();
        appender.verify(new RegexLogMatcher(".+CONNECT: 0.0.0.0/0.0.0.0:80$"));
    }

    @Test
    public void shouldLogChannelConnectWithLocalAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.connect(new InetSocketAddress(80), new InetSocketAddress(81)).asStage().await();
        appender.verify(new RegexLogMatcher(
                "^\\[id: 0xembedded, L:embedded - R:embedded\\] CONNECT: 0.0.0.0/0.0.0.0:80, 0.0.0.0/0.0.0.0:81$"));
    }

    @Test
    public void shouldLogChannelDisconnect() throws Exception {
        EmbeddedChannel channel = new DisconnectingEmbeddedChannel(newLoggingHandler());
        channel.connect(new InetSocketAddress(80)).asStage().await();
        channel.disconnect().asStage().await();
        appender.verify(new RegexLogMatcher(".+DISCONNECT$"));
    }

    @Test
    public void shouldLogChannelInactive() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.pipeline().fireChannelInactive();
        appender.verify(new RegexLogMatcher(".+INACTIVE$"));
    }

    @Test
    public void shouldLogChannelBind() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.bind(new InetSocketAddress(80));
        appender.verify(new RegexLogMatcher(".+BIND: 0.0.0.0/0.0.0.0:80$"));
    }

    @SuppressWarnings("StringOperationCanBeSimplified")
    @Test
    public void shouldLogChannelUserEvent() throws Exception {
        String userTriggered = "iAmCustom!";
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.pipeline().fireChannelInboundEvent(new String(userTriggered));
        appender.verify(new RegexLogMatcher(".+USER_EVENT: " + userTriggered + '$'));
    }

    @Test
    public void shouldLogChannelException() throws Exception {
        String msg = "illegalState";
        Throwable cause = new IllegalStateException(msg);
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.pipeline().fireChannelExceptionCaught(cause);
        appender.verify(new RegexLogMatcher(
                ".+EXCEPTION: " + cause.getClass().getCanonicalName() + ": " + msg));
    }

    @Test
    public void shouldLogDataWritten() throws Exception {
        String msg = "hello";
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.writeOutbound(msg);
        appender.verify(new RegexLogMatcher(".+WRITE: " + msg));
        appender.verify(new RegexLogMatcher(".+FLUSH"));
    }

    @Test
    public void shouldLogNonByteBufDataRead() throws Exception {
        String msg = "hello";
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.writeInbound(msg);
        appender.verify(new RegexLogMatcher(".+READ: " + msg));

        String handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogBufferDataRead() throws Exception {
        try (Buffer msg = preferredAllocator().copyOf("hello", StandardCharsets.UTF_8)) {
            EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
            channel.writeInbound(msg.copy());
            appender.verify(new RegexLogMatcher(".+READ: " + msg.readableBytes() + 'B', true));

            try (Buffer handledMsg = channel.readInbound()) {
                assertEquals(msg, handledMsg);
            }
            assertThat(channel.readInbound(), is(nullValue()));
        }
    }

    @Test
    public void shouldLogBufferDataReadWithSimpleFormat() throws Exception {
        try (Buffer msg = preferredAllocator().copyOf("hello", StandardCharsets.UTF_8)) {
            EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler(LogLevel.DEBUG, BufferFormat.SIMPLE));
            channel.writeInbound(msg.copy());
            appender.verify(new RegexLogMatcher(".+READ: " + msg.readableBytes() + 'B', false));

            try (Buffer handledMsg = channel.readInbound()) {
                assertEquals(msg, handledMsg);
            }
            assertThat(channel.readInbound(), is(nullValue()));
        }
    }

    @Test
    public void shouldLogEmptyBufferDataRead() throws Exception {
        try (Buffer msg = preferredAllocator().allocate(0)) {
            EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
            channel.writeInbound(msg.copy());
            appender.verify(new RegexLogMatcher(".+READ: 0B", false));

            try (Buffer handledMsg = channel.readInbound()) {
                assertEquals(msg, handledMsg);
                assertThat(channel.readInbound(), is(nullValue()));
            }
        }
    }

    @Test
    public void shouldLogChannelReadComplete() throws Exception {
        Buffer msg = preferredAllocator().allocate(0);
        EmbeddedChannel channel = new EmbeddedChannel(newLoggingHandler());
        channel.writeInbound(msg);
        appender.verify(new RegexLogMatcher(".+READ COMPLETE$"));
    }

    private LoggingHandler newLoggingHandler() {
        return newLoggingHandler(LoggingHandler.DEFAULT_LEVEL);
    }

    private LoggingHandler newLoggingHandler(LogLevel level) {
        return newLoggingHandler(level, BufferFormat.HEX_DUMP);
    }

    private LoggingHandler newLoggingHandler(LogLevel level, BufferFormat bufferFormat) {
        return new LoggingHandler(level, bufferFormat) {
            @Override
            protected Logger getLogger(Class<?> clazz) {
                return appender;
            }

            @Override
            protected Logger getLogger(String name) {
                return appender;
            }
        };
    }

    /**
     * A custom EasyMock matcher that matches on Logback messages.
     */
    private static final class RegexLogMatcher implements ArgumentMatcher<String> {

        private final String expected;
        private final boolean shouldContainNewline;
        private String actualMsg;

        RegexLogMatcher(String expected) {
            this(expected, false);
        }

        RegexLogMatcher(String expected, boolean shouldContainNewline) {
            this.expected = expected;
            this.shouldContainNewline = shouldContainNewline;
        }

        @Override
        public String toString() {
            return "Regex[" + expected + (shouldContainNewline? "] with newlines" : "]");
        }

        @Override
        @SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
        public boolean matches(String actual) {
            // Match only the first line to skip the validation of hex-dump format.
            actualMsg = actual.split("(?s)[\\r\\n]+")[0];
            if (actualMsg.matches(expected)) {
                // The presence of a newline implies a hex-dump was logged
                return actual.contains(NEWLINE) == shouldContainNewline;
            }
            return false;
        }
    }

    private static final class DisconnectingEmbeddedChannel extends EmbeddedChannel {

        private DisconnectingEmbeddedChannel(ChannelHandler... handlers) {
            super(true, handlers);
        }
    }
}
