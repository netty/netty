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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * Verifies the correct functionality of the {@link LoggingHandler}.
 */
public class LoggingHandlerTest {

    /**
     * Custom logback appender which gets used to match on log messages.
     */
    private Appender<ILoggingEvent> appender;

    @Before
    public void setupCustomAppender() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = createNiceMock(Appender.class);
        root.addAppender(appender);
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAcceptNullLogLevel() {
        LogLevel level = null;
        new LoggingHandler(level);
    }

    @Test
    public void shouldApplyCustomLogLevel() {
        LoggingHandler handler = new LoggingHandler("LoggingHandlerTest", LogLevel.INFO);
        assertEquals(LogLevel.INFO, handler.level());
    }

    @Test
    public void shouldLogChannelActive() {
        appender.doAppend(matchesLog(".+ACTIVE\\(\\)$"));
        replay(appender);
        new EmbeddedChannel(new LoggingHandler());
        verify(appender);
    }

    @Test
    public void shouldLogChannelRegistered() {
        appender.doAppend(matchesLog(".+REGISTERED\\(\\)$"));
        replay(appender);
        new EmbeddedChannel(new LoggingHandler());
        verify(appender);
    }

    @Test
    public void shouldLogChannelClose() throws Exception {
        appender.doAppend(matchesLog(".+CLOSE\\(\\)$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.close().await();
        verify(appender);
    }

    @Test
    public void shouldLogChannelConnect() throws Exception {
        appender.doAppend(matchesLog(".+CONNECT\\(0.0.0.0/0.0.0.0:80, null\\)$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80)).await();
        verify(appender);
    }

    @Test
    public void shouldLogChannelDisconnect() throws Exception {
        appender.doAppend(matchesLog(".+DISCONNECT\\(\\)$"));
        replay(appender);
        EmbeddedChannel channel = new DisconnectingEmbeddedChannel(new LoggingHandler());
        channel.connect(new InetSocketAddress(80)).await();
        channel.disconnect().await();
        verify(appender);
    }

    @Test
    public void shouldLogChannelInactive() throws Exception {
        appender.doAppend(matchesLog(".+INACTIVE\\(\\)$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireChannelInactive();
        verify(appender);
    }

    @Test
    public void shouldLogChannelBind() throws Exception {
        appender.doAppend(matchesLog(".+BIND\\(0.0.0.0/0.0.0.0:80\\)$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.bind(new InetSocketAddress(80));
        verify(appender);
    }

    @Test
    public void shouldLogChannelUserEvent() throws Exception {
        String userTriggered = "iAmCustom!";
        appender.doAppend(matchesLog(".+USER_EVENT\\(" + userTriggered + "\\)$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireUserEventTriggered(new String(userTriggered));
        verify(appender);
    }

    @Test
    public void shouldLogChannelException() throws Exception {
        String msg = "illegalState";
        Throwable cause = new IllegalStateException(msg);
        appender.doAppend(matchesLog(".+EXCEPTION\\(" + cause.getClass().getCanonicalName() + ": " + msg + "\\)$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.pipeline().fireExceptionCaught(cause);
        verify(appender);
    }

    @Test
    public void shouldLogDataWritten() throws Exception {
        String msg = "hello";
        appender.doAppend(matchesLog(".+WRITE\\(\\): " + msg + "$"));
        appender.doAppend(matchesLog(".+FLUSH\\(\\)"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeOutbound(msg);
        verify(appender);
    }

    @Test
    public void shouldLogDataRead() throws Exception {
        String msg = "hello";
        appender.doAppend(matchesLog(".+RECEIVED\\(\\): " + msg + "$"));
        replay(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new LoggingHandler());
        channel.writeInbound(msg);
        verify(appender);
    }

    /**
     * Static helper method for matching Logback messages.
     *
     * @param toMatch the regex to match.
     * @return a mocked event to pass into the {@link Appender#doAppend(Object)} method.
     */
    private static ILoggingEvent matchesLog(String toMatch) {
        EasyMock.reportMatcher(new RegexLogMatcher(toMatch));
        return null;
    }

    /**
     * A custom EasyMock matcher that matches on Logback messages.
     */
    private static class RegexLogMatcher implements IArgumentMatcher {

        private final String expected;
        private String actualMsg;

        public RegexLogMatcher(String expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(Object actual) {
            if (!(actual instanceof ILoggingEvent)) {
                return false;
            }

            actualMsg = ((ILoggingEvent) actual).getMessage();
            return actualMsg.matches(expected);
        }

        @Override
        public void appendTo(StringBuffer buffer) {
            buffer.append("matchesLog(");
            buffer.append("expected: \"" + expected);
            buffer.append("\", got: \"" + actualMsg);
            buffer.append("\")");
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
