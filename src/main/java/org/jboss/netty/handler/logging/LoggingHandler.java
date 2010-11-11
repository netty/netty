/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.logging;

import static org.jboss.netty.buffer.ChannelBuffers.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.logging.InternalLogLevel;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * A {@link ChannelHandler} that logs all events via {@link InternalLogger}.
 * By default, all events are logged at <tt>DEBUG</tt> level.  You can extend
 * this class and override {@link #log(ChannelEvent)} to change the default
 * behavior.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2121 $, $Date: 2010-02-02 09:38:07 +0900 (Tue, 02 Feb 2010) $
 *
 * @apiviz.landmark
 */
@Sharable
public class LoggingHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {

    private static final InternalLogLevel DEFAULT_LEVEL = InternalLogLevel.DEBUG;

    private final InternalLogger logger;
    private final InternalLogLevel level;
    private final boolean hexDump;

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance with hex dump enabled.
     */
    public LoggingHandler() {
        this(true);
    }

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance.
     *
     * @param level   the log level
     */
    public LoggingHandler(InternalLogLevel level) {
        this(level, true);
    }

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance.
     *
     * @param hexDump {@code true} if and only if the hex dump of the received
     *                message is logged
     */
    public LoggingHandler(boolean hexDump) {
        this(DEFAULT_LEVEL, hexDump);
    }

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance.
     *
     * @param level   the log level
     * @param hexDump {@code true} if and only if the hex dump of the received
     *                message is logged
     */
    public LoggingHandler(InternalLogLevel level, boolean hexDump) {
        if (level == null) {
            throw new NullPointerException("level");
        }

        logger = InternalLoggerFactory.getInstance(getClass());
        this.level = level;
        this.hexDump = hexDump;
    }

    /**
     * Creates a new instance with the specified logger name and with hex dump
     * enabled.
     */
    public LoggingHandler(Class<?> clazz) {
        this(clazz, true);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param hexDump {@code true} if and only if the hex dump of the received
     *                message is logged
     */
    public LoggingHandler(Class<?> clazz, boolean hexDump) {
        this(clazz, DEFAULT_LEVEL, hexDump);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param level   the log level
     */
    public LoggingHandler(Class<?> clazz, InternalLogLevel level) {
        this(clazz, level, true);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param level   the log level
     * @param hexDump {@code true} if and only if the hex dump of the received
     *                message is logged
     */
    public LoggingHandler(Class<?> clazz, InternalLogLevel level, boolean hexDump) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        if (level == null) {
            throw new NullPointerException("level");
        }
        logger = InternalLoggerFactory.getInstance(clazz);
        this.level = level;
        this.hexDump = hexDump;
    }

    /**
     * Creates a new instance with the specified logger name and with hex dump
     * enabled.
     */
    public LoggingHandler(String name) {
        this(name, true);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param hexDump {@code true} if and only if the hex dump of the received
     *                message is logged
     */
    public LoggingHandler(String name, boolean hexDump) {
        this(name, DEFAULT_LEVEL, hexDump);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param level   the log level
     * @param hexDump {@code true} if and only if the hex dump of the received
     *                message is logged
     */
    public LoggingHandler(String name, InternalLogLevel level, boolean hexDump) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (level == null) {
            throw new NullPointerException("level");
        }
        logger = InternalLoggerFactory.getInstance(name);
        this.level = level;
        this.hexDump = hexDump;
    }

    /**
     * Returns the {@link InternalLogger} that this handler uses to log
     * a {@link ChannelEvent}.
     */
    public InternalLogger getLogger() {
        return logger;
    }

    /**
     * Returns the {@link InternalLogLevel} that this handler uses to log
     * a {@link ChannelEvent}.
     */
    public InternalLogLevel getLevel() {
        return level;
    }

    /**
     * Logs the specified event to the {@link InternalLogger} returned by
     * {@link #getLogger()}. If hex dump has been enabled for this handler,
     * the hex dump of the {@link ChannelBuffer} in a {@link MessageEvent} will
     * be logged together.
     */
    public void log(ChannelEvent e) {
        if (getLogger().isEnabled(level)) {
            String msg = e.toString();

            // Append hex dump if necessary.
            if (hexDump && e instanceof MessageEvent) {
                MessageEvent me = (MessageEvent) e;
                if (me.getMessage() instanceof ChannelBuffer) {
                    ChannelBuffer buf = (ChannelBuffer) me.getMessage();
                    msg = msg + " - (HEXDUMP: " + hexDump(buf) + ')';
                }
            }

            // Log the message (and exception if available.)
            if (e instanceof ExceptionEvent) {
                getLogger().log(level, msg, ((ExceptionEvent) e).getCause());
            } else {
                getLogger().log(level, msg);
            }
        }
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        log(e);
        ctx.sendUpstream(e);
    }

    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        log(e);
        ctx.sendDownstream(e);
    }
}
