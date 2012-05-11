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

    private static final String[] BYTE2HEX = new String[256];
    private static final String[] HEXPADDING = new String[16];
    private static final String[] BYTEPADDING = new String[16];
    private static final char[] BYTE2CHAR = new char[256];

    static {
        int i;

        // Generate the lookup table for byte-to-hex-dump conversion
        for (i = 0; i < 10; i ++) {
            StringBuilder buf = new StringBuilder(3);
            buf.append(" 0");
            buf.append(i);
            BYTE2HEX[i] = buf.toString();
        }
        for (;i < 16; i ++) {
            StringBuilder buf = new StringBuilder(3);
            buf.append(" 0");
            buf.append((char) ('a' + i - 10));
            BYTE2HEX[i] = buf.toString();
        }
        for (; i < BYTE2HEX.length; i ++) {
            StringBuilder buf = new StringBuilder(3);
            buf.append(' ');
            buf.append(Integer.toHexString(i));
            BYTE2HEX[i] = buf.toString();
        }

        // Generate the lookup table for hex dump paddings
        for (i = 0; i < HEXPADDING.length; i ++) {
            int padding = HEXPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding * 3);
            for (int j = 0; j < padding; j ++) {
                buf.append("   ");
            }
            HEXPADDING[i] = buf.toString();
        }

        // Generate the lookup table for byte dump paddings
        for (i = 0; i < BYTEPADDING.length; i ++) {
            int padding = BYTEPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding);
            for (int j = 0; j < padding; j ++) {
                buf.append(' ');
            }
            BYTEPADDING[i] = buf.toString();
        }

        // Generate the lookup table for byte-to-char conversion
        for (i = 0; i < BYTE2CHAR.length; i ++) {
            if (i <= 0x1f || i >= 0x7f) {
                BYTE2CHAR[i] = '.';
            } else {
                BYTE2CHAR[i] = (char) i;
            }
        }
    }

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

        StringBuilder buf = new StringBuilder(bufName.length() + elemType.length() + content.length() + 16);
        buf.append(bufName);
        buf.append('[');
        buf.append(elemType);
        buf.append("](");
        buf.append(size);
        buf.append("): ");
        buf.append(content);
        return buf.toString();
    }

    private static String hexdump(ChannelBuffer buf) {
        int length = buf.readableBytes();
        int rows = length / 16 + (length % 15 == 0? 0 : 1) + 4;
        StringBuilder dump = new StringBuilder(rows * 80);

        dump.append(
                NEWLINE + "         +-------------------------------------------------+" +
                NEWLINE + "         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |" +
                NEWLINE + "+--------+-------------------------------------------------+----------------+");

        final int startIndex = buf.readerIndex();
        final int endIndex = buf.writerIndex();

        int i;
        for (i = startIndex; i < endIndex; i ++) {
            int relIdx = i - startIndex;
            int relIdxMod16 = relIdx & 15;
            if (relIdxMod16 == 0) {
                dump.append(NEWLINE);
                dump.append(Long.toHexString(relIdx & 0xFFFFFFFFL | 0x100000000L));
                dump.setCharAt(dump.length() - 9, '|');
                dump.append('|');
            }
            dump.append(BYTE2HEX[buf.getUnsignedByte(i)]);
            if (relIdxMod16 == 15) {
                dump.append(" |");
                for (int j = i - 15; j <= i; j ++) {
                    dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
                }
                dump.append('|');
            }
        }

        if ((i - startIndex & 15) != 0) {
            int remainder = length & 15;
            dump.append(HEXPADDING[remainder]);
            dump.append(" |");
            for (int j = i - remainder; j < i; j ++) {
                dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
            }
            dump.append(BYTEPADDING[remainder]);
            dump.append('|');
        }

        dump.append(
                NEWLINE + "+--------+-------------------------------------------------+----------------+");

        return dump.toString();
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
            logger.log(level, format(ctx, "EXCEPTION: " + cause), cause);
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelInboundHandlerContext<Object> ctx,
            Object evt) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "USER_EVENT: " + evt));
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
            logger.log(level, format(ctx, "BIND(" + localAddress + ')'));
        }
        super.bind(ctx, localAddress, future);
    }

    @Override
    public void connect(ChannelOutboundHandlerContext<Object> ctx,
            SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "CONNECT(" + remoteAddress + ", " + localAddress + ')'));
        }
        super.connect(ctx, remoteAddress, localAddress, future);
    }

    @Override
    public void disconnect(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "DISCONNECT()"));
        }
        super.disconnect(ctx, future);
    }

    @Override
    public void close(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "CLOSE()"));
        }
        super.close(ctx, future);
    }

    @Override
    public void deregister(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, "DEREGISTER()"));
        }
        super.deregister(ctx, future);
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<Object> ctx,
            ChannelFuture future) throws Exception {
        if (getLogger().isEnabled(level)) {
            logger.log(level, format(ctx, formatBuffer("OUTBUF", ctx.prevOut())));
        }
        ctx.flush(future);
    }
}
