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
package io.netty.handler.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentMatcher;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.netty.util.internal.StringUtil.NEWLINE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

/**
 * Verifies the correct functionality of the {@link LoggingHandler}.
 */
public class LoggingHandlerTest {

    private static final String LOGGER_NAME = LoggingHandler.class.getName();

    private static final Logger rootLogger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
    private static final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);

    private static final List<Appender<ILoggingEvent>> oldAppenders = new ArrayList<Appender<ILoggingEvent>>();
    /**
     * Custom logback appender which gets used to match on log messages.
     */
    private Appender<ILoggingEvent> appender;

    @BeforeAll
    public static void beforeClass() {
        for (Iterator<Appender<ILoggingEvent>> i = rootLogger.iteratorForAppenders(); i.hasNext();) {
            Appender<ILoggingEvent> a = i.next();
            oldAppenders.add(a);
            rootLogger.detachAppender(a);
        }

        Unpooled.buffer();
    }

    @AfterAll
    public static void afterClass() {
        for (Appender<ILoggingEvent> a: oldAppenders) {
            rootLogger.addAppender(a);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        appender = mock(Appender.class);
        logger.addAppender(appender);
    }

    @AfterEach
    public void teardown() {
        logger.detachAppender(appender);
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
        new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+ACTIVE$")));
    }

    @Test
    public void shouldLogChannelWritabilityChanged() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        // this is used to switch the channel to become unwritable
        channel.config().setWriteBufferLowWaterMark(5);
        channel.config().setWriteBufferHighWaterMark(10);
        channel.write("hello", channel.newPromise());

        verify(appender).doAppend(argThat(new RegexLogMatcher(".+WRITABILITY CHANGED$")));
    }

    @Test
    public void shouldLogChannelRegistered() {
        new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+REGISTERED$")));
    }

    @Test
    public void shouldLogChannelClose() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.close().await();
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+CLOSE$")));
    }

    @Test
    public void shouldLogChannelConnect() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.connect(new InetSocketAddress(80)).await();
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+CONNECT: 0.0.0.0/0.0.0.0:80$")));
    }

    @Test
    public void shouldLogChannelConnectWithLocalAddress() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.connect(new InetSocketAddress(80), new InetSocketAddress(81)).await();
        verify(appender).doAppend(argThat(new RegexLogMatcher(
                "^\\[id: 0xembedded, L:embedded - R:embedded\\] CONNECT: 0.0.0.0/0.0.0.0:80, 0.0.0.0/0.0.0.0:81$")));
    }

    @Test
    public void shouldLogChannelDisconnect() throws Exception {
        EmbeddedChannel channel = new DisconnectingEmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.connect(new InetSocketAddress(80)).await();
        channel.disconnect().await();
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+DISCONNECT$")));
    }

    @Test
    public void shouldLogChannelInactive() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.pipeline().fireChannelInactive();
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+INACTIVE$")));
    }

    @Test
    public void shouldLogChannelBind() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.bind(new InetSocketAddress(80));
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+BIND: 0.0.0.0/0.0.0.0:80$")));
    }

    @Test
    @SuppressWarnings("RedundantStringConstructorCall")
    public void shouldLogChannelUserEvent() throws Exception {
        String userTriggered = "iAmCustom!";
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.pipeline().fireUserEventTriggered(new String(userTriggered));
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+USER_EVENT: " + userTriggered + '$')));
    }

    @Test
    public void shouldLogChannelException() throws Exception {
        String msg = "illegalState";
        Throwable cause = new IllegalStateException(msg);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.pipeline().fireExceptionCaught(cause);
        verify(appender).doAppend(argThat(new RegexLogMatcher(
                ".+EXCEPTION: " + cause.getClass().getCanonicalName() + ": " + msg + '$')));
    }

    @Test
    public void shouldLogDataWritten() throws Exception {
        String msg = "hello";
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.writeOutbound(msg);
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+WRITE: " + msg + '$')));
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+FLUSH$")));
    }

    @Test
    public void shouldLogNonByteBufDataRead() throws Exception {
        String msg = "hello";
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.writeInbound(msg);
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+READ: " + msg + '$')));

        String handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogByteBufDataRead() throws Exception {
        ByteBuf msg = Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.writeInbound(msg);
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+READ: " + msg.readableBytes() + "B$", true)));

        ByteBuf handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        handledMsg.release();
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogByteBufDataReadWithSimpleFormat() throws Exception {
        ByteBuf msg = Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN, ByteBufFormat.SIMPLE));
        channel.writeInbound(msg);
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+READ: " + msg.readableBytes() + "B$", false)));

        ByteBuf handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        handledMsg.release();
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogEmptyByteBufDataRead() throws Exception {
        ByteBuf msg = Unpooled.EMPTY_BUFFER;
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.writeInbound(msg);
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+READ: 0B$", false)));

        ByteBuf handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogByteBufHolderDataRead() throws Exception {
        ByteBufHolder msg = new DefaultByteBufHolder(Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8)) {
            @Override
            public String toString() {
                return "foobar";
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.writeInbound(msg);
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+READ: foobar, 5B$", true)));

        ByteBufHolder handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        handledMsg.release();
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogChannelReadComplete() throws Exception {
        ByteBuf msg = Unpooled.EMPTY_BUFFER;
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler(LogLevel.WARN));
        channel.writeInbound(msg);
        verify(appender).doAppend(argThat(new RegexLogMatcher(".+READ COMPLETE$")));
    }

    /**
     * A custom EasyMock matcher that matches on Logback messages.
     */
    private static final class RegexLogMatcher implements ArgumentMatcher<ILoggingEvent> {

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
        @SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
        public boolean matches(ILoggingEvent actual) {
            // Match only the first line to skip the validation of hex-dump format.
            actualMsg = actual.getMessage().split("(?s)[\\r\\n]+")[0];
            if (actualMsg.matches(expected)) {
                // The presence of a newline implies a hex-dump was logged
                return actual.getMessage().contains(NEWLINE) == shouldContainNewline;
            }
            return false;
        }
    }

    private static final class DisconnectingEmbeddedChannel extends EmbeddedChannel {

        private DisconnectingEmbeddedChannel(ChannelHandler... handlers) {
            super(handlers);
        }

        @Override
        public ChannelMetadata metadata() {
            return new ChannelMetadata(true);
        }
    }
}
