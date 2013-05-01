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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;

/**
 * A {@link ChannelHandler} that logs all events using a logging framework.
 * By default, all events are logged at <tt>DEBUG</tt> level.
 */
@Sharable
public class LoggingHandler extends ChannelDuplexHandler {

    private static final LogLevel DEFAULT_LEVEL = LogLevel.DEBUG;

    protected final InternalLogger logger;
    protected final InternalLogLevel internalLevel;

    private final LogLevel level;

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance with hex dump enabled.
     */
    public LoggingHandler() {
        this(DEFAULT_LEVEL);
    }

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance.
     *
     * @param level   the log level
     */
    public LoggingHandler(LogLevel level) {
        if (level == null) {
            throw new NullPointerException("level");
        }

        logger = InternalLoggerFactory.getInstance(getClass());
        this.level = level;
        internalLevel = level.toInternalLevel();
    }

    /**
     * Creates a new instance with the specified logger name and with hex dump
     * enabled.
     */
    public LoggingHandler(Class<?> clazz) {
        this(clazz, DEFAULT_LEVEL);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param level   the log level
     */
    public LoggingHandler(Class<?> clazz, LogLevel level) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        if (level == null) {
            throw new NullPointerException("level");
        }
        logger = InternalLoggerFactory.getInstance(clazz);
        this.level = level;
        internalLevel = level.toInternalLevel();
    }

    /**
     * Creates a new instance with the specified logger name.
     */
    public LoggingHandler(String name) {
        this(name, DEFAULT_LEVEL);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param level   the log level
     */
    public LoggingHandler(String name, LogLevel level) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (level == null) {
            throw new NullPointerException("level");
        }
        logger = InternalLoggerFactory.getInstance(name);
        this.level = level;
        internalLevel = level.toInternalLevel();
    }

    /**
     * Returns the {@link LogLevel} that this handler uses to log
     */
    public LogLevel level() {
        return level;
    }

    protected String format(ChannelHandlerContext ctx, String message) {
        String chStr = ctx.channel().toString();
        StringBuilder buf = new StringBuilder(chStr.length() + message.length() + 1);
        buf.append(chStr);
        buf.append(' ');
        buf.append(message);
        return buf.toString();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx)
            throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "REGISTERED"));
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx)
            throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "UNREGISTERED"));
        }
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx)
            throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "ACTIVE"));
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx)
            throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "INACTIVE"));
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
            Throwable cause) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "EXCEPTION: " + cause), cause);
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx,
            Object evt) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "USER_EVENT: " + evt));
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void bind(ChannelHandlerContext ctx,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "BIND(" + localAddress + ')'));
        }
        super.bind(ctx, localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "CONNECT(" + remoteAddress + ", " + localAddress + ')'));
        }
        super.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx,
            ChannelPromise promise) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "DISCONNECT()"));
        }
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx,
            ChannelPromise promise) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "CLOSE()"));
        }
        super.close(ctx, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx,
             ChannelPromise promise) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "DEREGISTER()"));
        }
        super.deregister(ctx, promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise promise)
            throws Exception {
        ctx.flush(promise);
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx)
            throws Exception {
        ctx.fireInboundBufferUpdated();
    }
}
