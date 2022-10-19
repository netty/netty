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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.DefaultErrorHandler;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentMatcher;

import java.io.Serializable;
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
 * <p>
 * Test is {@link Isolated} because it modifies the shared, static logging configuration.
 */
@Isolated
public class LoggingHandlerTest {

    private static final String LOGGER_NAME = LoggingHandler.class.getName();

    private static final class TestAppender implements Appender {
        public static final String NAME = "TestAppender";

        private ArrayList<String> lines = new ArrayList<>();

        @Override
        public void append(LogEvent event) {
            synchronized (lines) {
                lines.add(event.getMessage().getFormattedMessage());
            }
        }

        @Override
        public String getName() {
            return NAME;
        }

        public AppenderRef ref() {
            return AppenderRef.createAppenderRef(getName(), null, null);
        }

        @Override
        public Layout<? extends Serializable> getLayout() {
            return null;
        }

        @Override
        public boolean ignoreExceptions() {
            return false;
        }

        @Override
        public ErrorHandler getHandler() {
            return new DefaultErrorHandler(this);
        }

        @Override
        public void setHandler(ErrorHandler handler) {
        }

        @Override
        public State getState() {
            return State.STARTED;
        }

        @Override
        public void initialize() {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        public void clear() {
            synchronized (lines) {
                lines.clear();
            }
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
     * Custom log4j appender which gets used to match on log messages.
     */
    private static final TestAppender appender = new TestAppender();

    @BeforeAll
    public static void setUpLogger() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = ctx.getConfiguration();
        configuration.addAppender(appender);
        LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withConfig(configuration)
                .withLoggerName(LOGGER_NAME)
                .withLevel(Level.DEBUG)
                .withRefs(new AppenderRef[]{appender.ref()})
                .build();
        loggerConfig.addAppender(appender, null, null);
        configuration.addLogger(LOGGER_NAME, loggerConfig);
        ctx.updateLoggers();
    }

    @AfterAll
    public static void remoteTestLogger() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = ctx.getConfiguration();
        configuration.removeLogger(LOGGER_NAME);
        ctx.updateLoggers();
    }

    @BeforeEach
    public void setup() {
        appender.clear();
    }

    @Test
    public void shouldNotAcceptNullLogLevel() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                LogLevel level = null;
                new LoggingHandler(level);
            }
        });
    }

    @Test
    public void shouldApplyCustomLogLevel() {
        LoggingHandler handler = new LoggingHandler(LogLevel.INFO);
        assertEquals(LogLevel.INFO, handler.level());
    }

    @Test
    public void shouldLogChannelActive() {
        new EmbeddedChannel(new LoggingHandler());
        appender.verify(new RegexLogMatcher(".+ACTIVE"));
    }

    @Test
    public void shouldLogChannelWritabilityChanged() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
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
        new EmbeddedChannel(new LoggingHandler());
        appender.verify(new RegexLogMatcher(".+REGISTERED$"));
    }

    @Test
    public void shouldLogChannelClose() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.close().asStage().await();
        appender.verify(new RegexLogMatcher(".+CLOSE$"));
    }

    @Test
    public void shouldLogChannelConnect() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80)).asStage().await();
        appender.verify(new RegexLogMatcher(".+CONNECT: 0.0.0.0/0.0.0.0:80$"));
    }

    @Test
    public void shouldLogChannelConnectWithLocalAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80), new InetSocketAddress(81)).asStage().await();
        appender.verify(new RegexLogMatcher(
                "^\\[id: 0xembedded, L:embedded - R:embedded\\] CONNECT: 0.0.0.0/0.0.0.0:80, 0.0.0.0/0.0.0.0:81$"));
    }

    @Test
    public void shouldLogChannelDisconnect() throws Exception {
        EmbeddedChannel channel = new DisconnectingEmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80)).asStage().await();
        channel.disconnect().asStage().await();
        appender.verify(new RegexLogMatcher(".+DISCONNECT$"));
    }

    @Test
    public void shouldLogChannelInactive() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireChannelInactive();
        appender.verify(new RegexLogMatcher(".+INACTIVE$"));
    }

    @Test
    public void shouldLogChannelBind() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.bind(new InetSocketAddress(80));
        appender.verify(new RegexLogMatcher(".+BIND: 0.0.0.0/0.0.0.0:80$"));
    }

    @SuppressWarnings("StringOperationCanBeSimplified")
    @Test
    public void shouldLogChannelUserEvent() throws Exception {
        String userTriggered = "iAmCustom!";
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireChannelInboundEvent(new String(userTriggered));
        appender.verify(new RegexLogMatcher(".+USER_EVENT: " + userTriggered + '$'));
    }

    @Test
    public void shouldLogChannelException() throws Exception {
        String msg = "illegalState";
        Throwable cause = new IllegalStateException(msg);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireChannelExceptionCaught(cause);
        appender.verify(new RegexLogMatcher(
                ".+EXCEPTION: " + cause.getClass().getCanonicalName() + ": " + msg));
    }

    @Test
    public void shouldLogDataWritten() throws Exception {
        String msg = "hello";
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeOutbound(msg);
        appender.verify(new RegexLogMatcher(".+WRITE: " + msg));
        appender.verify(new RegexLogMatcher(".+FLUSH"));
    }

    @Test
    public void shouldLogNonByteBufDataRead() throws Exception {
        String msg = "hello";
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeInbound(msg);
        appender.verify(new RegexLogMatcher(".+READ: " + msg));

        String handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogBufferDataRead() throws Exception {
        try (Buffer msg = preferredAllocator().copyOf("hello", StandardCharsets.UTF_8)) {
            EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
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
            EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.DEBUG, BufferFormat.SIMPLE));
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
            EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
            channel.writeInbound(msg.copy());
            appender.verify(new RegexLogMatcher(".+READ: 0B", false));

            Buffer handledMsg = channel.readInbound();
            assertEquals(msg, handledMsg);
            assertThat(channel.readInbound(), is(nullValue()));
        }
    }

    @Test
    public void shouldLogChannelReadComplete() throws Exception {
        Buffer msg = preferredAllocator().allocate(0);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeInbound(msg);
        appender.verify(new RegexLogMatcher(".+READ COMPLETE$"));
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
