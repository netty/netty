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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufProcessor;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * A decoder that splits the received {@link ByteBuf}s on line endings.
 * <p>
 * Both {@code "\n"} and {@code "\r\n"} are handled.
 * For a more general delimiter-based decoder, see {@link DelimiterBasedFrameDecoder}.
 * 以固定的分隔符去拆分输入流中数据
 * 即从输入中按照\r\n的方式,拆分数据,获取第一部分数据
 */
public class LineBasedFrameDecoder extends ByteToMessageDecoder {

    /** Maximum length of a frame we're willing to decode.  */
    private final int maxLength;//设置最大包长度
    /** Whether or not to throw an exception as soon as we exceed maxLength. */
    private final boolean failFast;//超出最大长度后,是否要立即抛出异常。true立刻抛出异常 false表示晚一些抛出
    private final boolean stripDelimiter;//true表示丢弃\r\n内容  false表示保留该内容

    /** True if we're discarding input because we're already over maxLength.  */
    private boolean discarding;//默认false  true表示要丢弃输入内容,因为计算的输入已经超出了限制  true进入丢弃模式
    private int discardedBytes;//丢弃的字节大小

    /** Last scan position. */
    private int offset;

    /**
     * Creates a new decoder.
     * @param maxLength  the maximum length of the decoded frame.
     *                   A {@link TooLongFrameException} is thrown if
     *                   the length of the frame exceeds this value.
     */
    public LineBasedFrameDecoder(final int maxLength) {
        this(maxLength, true, false);
    }

    /**
     * Creates a new decoder.
     * @param maxLength  the maximum length of the decoded frame.
     *                   A {@link TooLongFrameException} is thrown if
     *                   the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not true表示要丢弃输入内容,因为计算的输入已经超出了限制,即拆包后,是否保留原始拆分字符串内容.默认是false,表示保留\r\n
     * @param failFast  If <tt>true</tt>, a {@link TooLongFrameException} is
     *                  thrown as soon as the decoder notices the length of the
     *                  frame will exceed <tt>maxFrameLength</tt> regardless of
     *                  whether the entire frame has been read.
     *                  If <tt>false</tt>, a {@link TooLongFrameException} is
     *                  thrown after the entire frame that exceeds
     *                  <tt>maxFrameLength</tt> has been read.
     */
    public LineBasedFrameDecoder(final int maxLength, final boolean stripDelimiter, final boolean failFast) {
        this.maxLength = maxLength;
        this.failFast = failFast;
        this.stripDelimiter = stripDelimiter;
    }

    /**
     * 从in中读取一个对象,添加到out集合中
     * ctx上下文,用于向上handler处理异常
     */
    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param   ctx             the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param   buffer          the {@link ByteBuf} from which to read data
     * @return  frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                          be created.
     */
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        final int eol = findEndOfLine(buffer);//找到分隔符位置
        if (!discarding) {
            if (eol >= 0) {
                final ByteBuf frame;
                final int length = eol - buffer.readerIndex();
                final int delimLength = buffer.getByte(eol) == '\r'? 2 : 1;

                if (length > maxLength) {//有效字符超过最大字符限制
                    buffer.readerIndex(eol + delimLength);//重新设置新的位置--丢弃
                    fail(ctx, length);
                    return null;//返回null,表示没有解析成功---父类会break本次字节处理流程
                }

                if (stripDelimiter) {
                    frame = buffer.readSlice(length);
                    buffer.skipBytes(delimLength);
                } else {
                    frame = buffer.readSlice(length + delimLength);
                }

                return frame.retain();
            } else {//说明此时input无\r\n
                final int length = buffer.readableBytes();
                if (length > maxLength) {//超出限制
                    discardedBytes = length;//丢弃的长度
                    buffer.readerIndex(buffer.writerIndex());//重新设置下一次读取的位置
                    discarding = true;//进入丢弃模式
                    offset = 0;
                    if (failFast) {//立刻触发异常
                        fail(ctx, "over " + discardedBytes);
                    }
                }
                return null;
            }
        } else {//说明已经丢弃
            if (eol >= 0) {
                //length表示最终丢弃了多少个字节
                final int length = discardedBytes + eol - buffer.readerIndex();//记录最终丢弃了多少个字节  上一次丢弃的 + 本次丢弃的
                final int delimLength = buffer.getByte(eol) == '\r'? 2 : 1;
                buffer.readerIndex(eol + delimLength);//移动到\r\n的位置,因为已经被丢弃了
                discardedBytes = 0;//重置丢弃长度
                discarding = false;//无丢弃模式
                if (!failFast) {//晚一些触发异常--与上面的if (failFast) 相反.表示只能触发一次,要么是立刻触发,要么是等一会触发
                    fail(ctx, length);
                }
            } else {
                discardedBytes += buffer.readableBytes();//累加丢弃字节数---因为依然没有找到\r\n,继续丢弃
                buffer.readerIndex(buffer.writerIndex());//切换到当前最新写的位置
            }
            return null;//已经丢弃了,肯定返回值就是null---因为到结束字节为止,都没有找到\r\n,因此继续等待读取新的字节,此时返回null,让父类触发break操作。
        }
    }

    private void fail(final ChannelHandlerContext ctx, int length) {
        fail(ctx, String.valueOf(length));
    }

    //向上报告异常,提示本身长度,超过了设置的长度上限
    private void fail(final ChannelHandlerContext ctx, String length) {
        ctx.fireExceptionCaught(
                new TooLongFrameException(
                        "frame length (" + length + ") exceeds the allowed maximum (" + maxLength + ')'));
    }

    /**
     * Returns the index in the buffer of the end of line found.
     * Returns -1 if no end of line was found in the buffer.
     * 找到\r\n或者\n之前的位置
     */
    private int findEndOfLine(final ByteBuf buffer) {
        int totalLength = buffer.readableBytes();
        int i = buffer.forEachByte(buffer.readerIndex() + offset, totalLength - offset, ByteBufProcessor.FIND_LF);//FIND_LF表示找到\n字符串就结束
        if (i >= 0) {
            offset = 0;
            if (i > 0 && buffer.getByte(i - 1) == '\r') {//说明此时匹配的是\r\n
                i--;//倒退一步,即\r也不要了
            }
        } else {
            offset = totalLength;
        }
        return i;
    }
}
