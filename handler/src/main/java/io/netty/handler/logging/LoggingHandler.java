/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.logging;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelOutboundHandlerContext;
import io.netty.logging.InternalLogLevel;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.Queue;

/**
 * A {@link ChannelHandler} that logs all events via {@link InternalLogger}.
 * By default, all events are logged at <tt>DEBUG</tt> level.  You can extend
 * this class and override {@link #log(ChannelEvent)} to change the default
 * behavior.
 * @apiviz.landmark
 */
@Sharable
public class LoggingHandler extends ChannelHandlerAdapter<Object, Object> {

    private static final InternalLogLevel DEFAULT_LEVEL = InternalLogLevel.DEBUG;
    private static final String NEWLINE = String.format("%n");

    private final InternalLogger logger;
    private final InternalLogLevel level;

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
    public LoggingHandler(InternalLogLevel level) {
        if (level == null) {
            throw new NullPointerException("level");
        }

        logger = InternalLoggerFactory.getInstance(getClass());
        this.level = level;
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
    public LoggingHandler(Class<?> clazz, InternalLogLevel level) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        if (level == null) {
            throw new NullPointerException("level");
        }
        logger = InternalLoggerFactory.getInstance(clazz);
        this.level = level;
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

    protected String format(ChannelHandlerContext ctx, String message) {
        String chStr = ctx.channel().toString();
        StringBuilder buf = new StringBuilder(chStr.length() + message.length() + 1);
        buf.append(chStr);
        buf.append(' ');
        buf.append(message);
        return buf.toString();
    }

    protected String formatBuffer(String bufName, ChannelBufferHolder<Object> holder) {
        String content;
        int size;
        String elemType;
        if (holder.hasByteBuffer()) {
            ChannelBuffer buf = holder.byteBuffer();
            size = buf.readableBytes();
            elemType = "Byte";
            content = hexdump(buf);
        } else {
            Queue<Object> buf = holder.messageBuffer();
            content = buf.toString();
            size = buf.size();
            elemType = "Object";
        }

        return String.format("%s[%s](%d): %s", bufName, elemType, size, content);
    }

    private static String hexdump(ChannelBuffer buf) {
        // TODO: Optimize using lookup tables
        int length = buf.readableBytes();
        int rows = length / 16 + (length % 15 == 0? 0 : 1) + 4;
        StringBuilder dump = new StringBuilder(rows * 80);

        dump.append(NEWLINE);
        dump.append("         +-------------------------------------------------+");
        dump.append(NEWLINE);
        dump.append("         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |");
        dump.append(NEWLINE);
        dump.append("+--------+-------------------------------------------------+----------------+");

        int i;
        for (i = buf.readerIndex(); i < buf.writerIndex(); i ++) {
            int relIdx = i - buf.readerIndex();
            int relIdxMod16 = relIdx & 15;
            if (relIdxMod16 == 0) {
                dump.append(NEWLINE);
                dump.append(String.format("|%8x|", relIdx));
            }
            dump.append(String.format(" %02x", buf.getUnsignedByte(i)));
            if (relIdxMod16 == 15) {
                dump.append(" |");
                for (int j = i - 15; j <= i; j ++) {
                    dump.append(toChar(buf.getByte(j)));
                }
                dump.append('|');
            }
        }
        if ((i - buf.readerIndex() & 15) != 0) {
            int remainder = length & 15;
            for (int j = 16 - remainder; j > 0; j --) {
                dump.append("   ");
            }
            dump.append(" |");
            for (int j = i - remainder; j < i; j ++) {
                dump.append(toChar(buf.getByte(j)));
            }
            for (int j = 16 - remainder; j > 0; j --) {
                dump.append(' ');
            }
            dump.append('|');
        }

        dump.append(NEWLINE);
        dump.append("+--------+-------------------------------------------------+----------------+");
        return dump.toString();
    }

    private static char toChar(int ch) {
        ch = ch & 0xff;
        if (ch <= 0x1f || ch >= 0x7f) {
            return '.';
        } else {
            return (char) ch;
        }
    }

    @Override
    public ChannelBufferHolder<Object> newOutboundBuffer(
            ChannelOutboundHandlerContext<Object> ctx) throws Exception {
        return ChannelBufferHolders.outboundBypassBuffer(ctx);
    }

    @Override
    public ChannelBufferHolder<Object> newInboundBuffer(
            ChannelInboundHandlerContext<Object> ctx) throws Exception {
        return ChannelBufferHolders.inboundBypassBuffer(ctx);
    }

    @Override
    public void channelRegistered(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "REGISTERED"));
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "UNREGISTERED"));
        }
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "ACTIVE"));
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "INACTIVE"));
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<Object> ctx,
            Throwable cause) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, String.format("EXCEPTION: %s", cause)), cause);
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelInboundHandlerContext<Object> ctx,
            Object evt) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, String.format("USER_EVENT: %s", evt)));
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Object> ctx)
            throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, formatBuffer("INBUF", ctx.in())));
        }
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void bind(ChannelOutboundHandlerContext<Object> ctx,
            SocketAddress localAddress, ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, String.format("bind(%s)", localAddress)));
        }
        super.bind(ctx, localAddress, future);
    }

    @Override
    public void connect(ChannelOutboundHandlerContext<Object> ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, String.format("connect(%s, %s)", remoteAddress, localAddress)));
        }
        super.connect(ctx, remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "disconnect()"));
        }
        super.disconnect(ctx, future);
    }

    @Override
    public void close(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "close()"));
        }
        super.close(ctx, future);
    }

    @Override
    public void deregister(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "deregister()"));
        }
        super.deregister(ctx, future);
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "flush()"));
        }
        super.flush(ctx, future);
    }
}
