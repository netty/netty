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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufConvertible;
import io.netty5.buffer.ByteBufHolder;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandler.Sharable;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.logging.InternalLogLevel;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;

import static io.netty5.buffer.ByteBufUtil.appendPrettyHexDump;
import static io.netty5.util.internal.StringUtil.NEWLINE;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ChannelHandler} that logs all events using a logging framework.
 * By default, all events are logged at <tt>DEBUG</tt> level and full hex dumps are recorded for ByteBufs.
 */
@Sharable
@SuppressWarnings({ "StringBufferReplaceableByString" })
public class LoggingHandler implements ChannelHandler {

    private static final LogLevel DEFAULT_LEVEL = LogLevel.DEBUG;

    protected final InternalLogger logger;
    protected final InternalLogLevel internalLevel;

    private final LogLevel level;
    private final ByteBufFormat byteBufFormat;

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
     * @param format Format of ByteBuf dumping
     */
    public LoggingHandler(ByteBufFormat format) {
        this(DEFAULT_LEVEL, format);
    }

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance.
     *
     * @param level the log level
     */
    public LoggingHandler(LogLevel level) {
        this(level, ByteBufFormat.HEX_DUMP);
    }

    /**
     * Creates a new instance whose logger name is the fully qualified class
     * name of the instance.
     *
     * @param level the log level
     * @param byteBufFormat the ByteBuf format
     */
    public LoggingHandler(LogLevel level, ByteBufFormat byteBufFormat) {
        this.level = requireNonNull(level, "level");
        this.byteBufFormat = requireNonNull(byteBufFormat, "byteBufFormat");
        logger = InternalLoggerFactory.getInstance(getClass());
        internalLevel = level.toInternalLevel();
    }

    /**
     * Creates a new instance with the specified logger name and with hex dump
     * enabled.
     *
     * @param clazz the class type to generate the logger for
     */
    public LoggingHandler(Class<?> clazz) {
        this(clazz, DEFAULT_LEVEL);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param clazz the class type to generate the logger for
     * @param level the log level
     */
    public LoggingHandler(Class<?> clazz, LogLevel level) {
        this(clazz, level, ByteBufFormat.HEX_DUMP);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param clazz the class type to generate the logger for
     * @param level the log level
     * @param byteBufFormat the ByteBuf format
     */
    public LoggingHandler(Class<?> clazz, LogLevel level, ByteBufFormat byteBufFormat) {
        requireNonNull(clazz, "clazz");
        this.level = requireNonNull(level, "level");
        this.byteBufFormat = requireNonNull(byteBufFormat, "byteBufFormat");
        logger = InternalLoggerFactory.getInstance(clazz);
        internalLevel = level.toInternalLevel();
    }

    /**
     * Creates a new instance with the specified logger name using the default log level.
     *
     * @param name the name of the class to use for the logger
     */
    public LoggingHandler(String name) {
        this(name, DEFAULT_LEVEL);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param name the name of the class to use for the logger
     * @param level the log level
     */
    public LoggingHandler(String name, LogLevel level) {
        this(name, level, ByteBufFormat.HEX_DUMP);
    }

    /**
     * Creates a new instance with the specified logger name.
     *
     * @param name the name of the class to use for the logger
     * @param level the log level
     * @param byteBufFormat the ByteBuf format
     */
    public LoggingHandler(String name, LogLevel level, ByteBufFormat byteBufFormat) {
        requireNonNull(name, "name");

        this.level = requireNonNull(level, "level");
        this.byteBufFormat = requireNonNull(byteBufFormat, "byteBufFormat");
        logger = InternalLoggerFactory.getInstance(name);
        internalLevel = level.toInternalLevel();
    }

    /**
     * Returns the {@link LogLevel} that this handler uses to log
     */
    public LogLevel level() {
        return level;
    }

    /**
     * Returns the {@link ByteBufFormat} that this handler uses to log
     */
    public ByteBufFormat byteBufFormat() {
        return byteBufFormat;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "REGISTERED"));
        }
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "UNREGISTERED"));
        }
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "ACTIVE"));
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "INACTIVE"));
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "EXCEPTION", cause), cause);
        }
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "USER_EVENT", evt));
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "BIND", localAddress));
        }
        return ctx.bind(localAddress);
    }

    @Override
    public Future<Void> connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "CONNECT", remoteAddress, localAddress));
        }
        return ctx.connect(remoteAddress, localAddress);
    }

    @Override
    public Future<Void> disconnect(ChannelHandlerContext ctx) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "DISCONNECT"));
        }
        return ctx.disconnect();
    }

    @Override
    public Future<Void> close(ChannelHandlerContext ctx) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "CLOSE"));
        }
        return ctx.close();
    }

    @Override
    public Future<Void> deregister(ChannelHandlerContext ctx) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "DEREGISTER"));
        }
        return ctx.deregister();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "READ COMPLETE"));
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "READ", msg));
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "WRITE", msg));
        }
        return ctx.write(msg);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "WRITABILITY CHANGED"));
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, "FLUSH"));
        }
        ctx.flush();
    }

    /**
     * Formats an event and returns the formatted message.
     *
     * @param eventName the name of the event
     */
    protected String format(ChannelHandlerContext ctx, String eventName) {
        String chStr = ctx.channel().toString();
        return new StringBuilder(chStr.length() + 1 + eventName.length())
            .append(chStr)
            .append(' ')
            .append(eventName)
            .toString();
    }

    /**
     * Formats an event and returns the formatted message.
     *
     * @param eventName the name of the event
     * @param arg       the argument of the event
     */
    protected String format(ChannelHandlerContext ctx, String eventName, Object arg) {
        if (arg instanceof ByteBufConvertible) {
            return formatByteBuf(ctx, eventName, ((ByteBufConvertible) arg).asByteBuf());
        } else if (arg instanceof ByteBufHolder) {
            return formatByteBufHolder(ctx, eventName, (ByteBufHolder) arg);
        } else {
            return formatSimple(ctx, eventName, arg);
        }
    }

    /**
     * Formats an event and returns the formatted message.  This method is currently only used for formatting
     * {@link ChannelHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress)}.
     *
     * @param eventName the name of the event
     * @param firstArg  the first argument of the event
     * @param secondArg the second argument of the event
     */
    protected String format(ChannelHandlerContext ctx, String eventName, Object firstArg, Object secondArg) {
        if (secondArg == null) {
            return formatSimple(ctx, eventName, firstArg);
        }

        String chStr = ctx.channel().toString();
        String arg1Str = String.valueOf(firstArg);
        String arg2Str = secondArg.toString();
        StringBuilder buf = new StringBuilder(
                chStr.length() + 1 + eventName.length() + 2 + arg1Str.length() + 2 + arg2Str.length());
        buf.append(chStr).append(' ').append(eventName).append(": ").append(arg1Str).append(", ").append(arg2Str);
        return buf.toString();
    }

    /**
     * Generates the default log message of the specified event whose argument is a {@link ByteBuf}.
     */
    private String formatByteBuf(ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
        String chStr = ctx.channel().toString();
        int length = msg.readableBytes();
        if (length == 0) {
            StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 4);
            buf.append(chStr).append(' ').append(eventName).append(": 0B");
            return buf.toString();
        } else {
            int outputLength = chStr.length() + 1 + eventName.length() + 2 + 10 + 1;
            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
                int rows = length / 16 + (length % 15 == 0? 0 : 1) + 4;
                int hexDumpLength = 2 + rows * 80;
                outputLength += hexDumpLength;
            }
            StringBuilder buf = new StringBuilder(outputLength);
            buf.append(chStr).append(' ').append(eventName).append(": ").append(length).append('B');
            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
                buf.append(NEWLINE);
                appendPrettyHexDump(buf, msg);
            }

            return buf.toString();
        }
    }

    /**
     * Generates the default log message of the specified event whose argument is a {@link ByteBufHolder}.
     */
    private String formatByteBufHolder(ChannelHandlerContext ctx, String eventName, ByteBufHolder msg) {
        String chStr = ctx.channel().toString();
        String msgStr = msg.toString();
        ByteBuf content = msg.content();
        int length = content.readableBytes();
        if (length == 0) {
            StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + msgStr.length() + 4);
            buf.append(chStr).append(' ').append(eventName).append(", ").append(msgStr).append(", 0B");
            return buf.toString();
        } else {
            int outputLength = chStr.length() + 1 + eventName.length() + 2 + msgStr.length() + 2 + 10 + 1;
            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
                int rows = length / 16 + (length % 15 == 0? 0 : 1) + 4;
                int hexDumpLength = 2 + rows * 80;
                outputLength += hexDumpLength;
            }
            StringBuilder buf = new StringBuilder(outputLength);
            buf.append(chStr).append(' ').append(eventName).append(": ")
               .append(msgStr).append(", ").append(length).append('B');
            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
                buf.append(NEWLINE);
                appendPrettyHexDump(buf, content);
            }

            return buf.toString();
        }
    }

    /**
     * Generates the default log message of the specified event whose argument is an arbitrary object.
     */
    private static String formatSimple(ChannelHandlerContext ctx, String eventName, Object msg) {
        String chStr = ctx.channel().toString();
        String msgStr = String.valueOf(msg);
        StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + msgStr.length());
        return buf.append(chStr).append(' ').append(eventName).append(": ").append(msgStr).toString();
    }
}
