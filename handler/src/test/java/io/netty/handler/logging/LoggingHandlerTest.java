/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import org.easymock.IArgumentMatcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reportMatcher;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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

    @BeforeClass
    public static void beforeClass() {
        for (Iterator<Appender<ILoggingEvent>> i = rootLogger.iteratorForAppenders(); i.hasNext();) {
            Appender<ILoggingEvent> a = i.next();
            oldAppenders.add(a);
            rootLogger.detachAppender(a);
        }

        Unpooled.buffer();
    }

    @AfterClass
    public static void afterClass() {
        for (Appender<ILoggingEvent> a: oldAppenders) {
            rootLogger.addAppender(a);
        }
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        appender = createNiceMock(Appender.class);
        logger.addAppender(appender);
    }

    @After
    public void teardown() {
        logger.detachAppender(appender);
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAcceptNullLogLevel() {
        LogLevel level = null;
        new LoggingHandler(level);
    }

    @Test
    public void shouldApplyCustomLogLevel() {
        LoggingHandler handler = new LoggingHandler(LogLevel.INFO);
        assertEquals(LogLevel.INFO, handler.level());
    }

    @Test
    public void shouldLogChannelActive() {
        appender.doAppend(matchesLog(".+ACTIVE$"));
        replay(appender);
        new EmbeddedChannel(new LoggingHandler());
        verify(appender);
    }

    @Test
    public void shouldLogChannelRegistered() {
        appender.doAppend(matchesLog(".+REGISTERED$"));
        replay(appender);
        new EmbeddedChannel(new LoggingHandler());
        verify(appender);
    }

    @Test
    public void shouldLogChannelClose() throws Exception {
        appender.doAppend(matchesLog(".+CLOSE$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.close().await();
        verify(appender);
    }

    @Test
    public void shouldLogChannelConnect() throws Exception {
        appender.doAppend(matchesLog(".+CONNECT: 0.0.0.0/0.0.0.0:80$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80)).await();
        verify(appender);
    }

    @Test
    public void shouldLogChannelConnectWithLocalAddress() throws Exception {
        appender.doAppend(matchesLog(".+CONNECT: 0.0.0.0/0.0.0.0:80, 0.0.0.0/0.0.0.0:81$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80), new InetSocketAddress(81)).await();
        verify(appender);
    }

    @Test
    public void shouldLogChannelDisconnect() throws Exception {
        appender.doAppend(matchesLog(".+DISCONNECT$"));
        replay(appender);
        EmbeddedChannel channel = new DisconnectingEmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80)).await();
        channel.disconnect().await();
        verify(appender);
    }

    @Test
    public void shouldLogChannelInactive() throws Exception {
        appender.doAppend(matchesLog(".+INACTIVE$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireChannelInactive();
        verify(appender);
    }

    @Test
    public void shouldLogChannelBind() throws Exception {
        appender.doAppend(matchesLog(".+BIND: 0.0.0.0/0.0.0.0:80$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.bind(new InetSocketAddress(80));
        verify(appender);
    }

    @Test
    @SuppressWarnings("RedundantStringConstructorCall")
    public void shouldLogChannelUserEvent() throws Exception {
        String userTriggered = "iAmCustom!";
        appender.doAppend(matchesLog(".+USER_EVENT: " + userTriggered + '$'));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireUserEventTriggered(new String(userTriggered));
        verify(appender);
    }

    @Test
    public void shouldLogChannelException() throws Exception {
        String msg = "illegalState";
        Throwable cause = new IllegalStateException(msg);
        appender.doAppend(matchesLog(".+EXCEPTION: " + cause.getClass().getCanonicalName() + ": " + msg + '$'));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireExceptionCaught(cause);
        verify(appender);
    }

    @Test
    public void shouldLogDataWritten() throws Exception {
        String msg = "hello";
        appender.doAppend(matchesLog(".+WRITE: " + msg + '$'));
        appender.doAppend(matchesLog(".+FLUSH$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeOutbound(msg);
        verify(appender);
    }

    @Test
    public void shouldLogNonByteBufDataRead() throws Exception {
        String msg = "hello";
        appender.doAppend(matchesLog(".+RECEIVED: " + msg + '$'));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeInbound(msg);
        verify(appender);

        String handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogByteBufDataRead() throws Exception {
        ByteBuf msg = Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8);
        appender.doAppend(matchesLog(".+RECEIVED: " + msg.readableBytes() + "B$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeInbound(msg);
        verify(appender);

        ByteBuf handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        handledMsg.release();
        assertThat(channel.readInbound(), is(nullValue()));
    }

    @Test
    public void shouldLogEmptyByteBufDataRead() throws Exception {
        ByteBuf msg = Unpooled.EMPTY_BUFFER;
        appender.doAppend(matchesLog(".+RECEIVED: 0B$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeInbound(msg);
        verify(appender);

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

        appender.doAppend(matchesLog(".+RECEIVED: foobar, 5B$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeInbound(msg);
        verify(appender);

        ByteBufHolder handledMsg = channel.readInbound();
        assertThat(msg, is(sameInstance(handledMsg)));
        handledMsg.release();
        assertThat(channel.readInbound(), is(nullValue()));
    }

    /**
     * Static helper method for matching Logback messages.
     *
     * @param toMatch the regex to match.
     * @return a mocked event to pass into the {@link Appender#doAppend(Object)} method.
     */
    private static ILoggingEvent matchesLog(String toMatch) {
        reportMatcher(new RegexLogMatcher(toMatch));
        return null;
    }

    /**
     * A custom EasyMock matcher that matches on Logback messages.
     */
    private static final class RegexLogMatcher implements IArgumentMatcher {

        private final String expected;
        private String actualMsg;

        RegexLogMatcher(String expected) {
            this.expected = expected;
        }

        @Override
        @SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
        public boolean matches(Object actual) {
            if (!(actual instanceof ILoggingEvent)) {
                return false;
            }

            // Match only the first line to skip the validation of hex-dump format.
            actualMsg = ((ILoggingEvent) actual).getMessage().split("(?s)[\\r\\n]+")[0];
            return actualMsg.matches(expected);
        }

        @Override
        public void appendTo(StringBuffer buffer) {
            buffer.append("matchesLog(")
                  .append("expected: \"").append(expected)
                  .append("\", got: \"").append(actualMsg)
                  .append("\")");
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
