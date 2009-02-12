/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.http;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;

/**
 * Decodes an Http type message.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public abstract class HttpMessageDecoder extends ReplayingDecoder<HttpMessageDecoder.State> {

    private static final Pattern INITIAL_PATTERN = Pattern.compile(
            "^\\s*(\\S+)\\s+(\\S+)\\s+(.*)\\s*$");
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^\\s*(\\S+)\\s*:\\s*(.*)\\s*$");

    protected HttpMessage message;
    private ChannelBuffer content;
    private int chunkSize;

    /**
     * @author The Netty Project (netty-dev@lists.jboss.org)
     * @author Trustin Lee (tlee@redhat.com)
     * @version $Rev$, $Date$
     *
     * @apiviz.exclude
     */
    protected enum State {
        SKIP_CONTROL_CHARS,
        READ_INITIAL,
        READ_HEADER,
        READ_CONTENT,
        READ_FIXED_LENGTH_CONTENT,
        READ_CHUNK_SIZE,
        READ_CHUNKED_CONTENT,
        READ_CHUNK_DELIMITER,
        READ_CHUNK_FOOTER;
    }

    protected HttpMessageDecoder() {
        super(State.SKIP_CONTROL_CHARS);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, State state) throws Exception {
        switch (state) {
        case SKIP_CONTROL_CHARS: {
            try {
                skipControlCharacters(buffer);
                checkpoint(State.READ_INITIAL);
            } finally {
                checkpoint();
            }
        }
        case READ_INITIAL: {
            readInitial(buffer);
        }
        case READ_HEADER: {
            readHeaders(buffer);
            if (message.isChunked()) {
                checkpoint(State.READ_CHUNK_SIZE);
            } else if (message.getContentLength() == 0) {
                content = ChannelBuffers.EMPTY_BUFFER;
                return reset();
            }
            //we return null here, this forces decode to be called again where we will decode the content
            return null;
        }
        case READ_CONTENT: {
            if (content == null) {
                content = ChannelBuffers.dynamicBuffer(channel.getConfig().getBufferFactory());
            }
            //this will cause a replay error until the channel is closed where this will read whats left in the buffer
            content.writeBytes(buffer.readBytes(buffer.readableBytes()));
            return reset();
        }
        case READ_FIXED_LENGTH_CONTENT: {
            //we have a content-length so we just read the correct number of bytes
            readFixedLengthContent(buffer);
            return reset();
        }
        /**
         * everything else after this point takes care of reading chunked content. basically, read chunk size,
         * read chunk, read and ignore the CRLF and repeat until 0
         */
        case READ_CHUNK_SIZE: {
            String line = readIntoCurrentLine(buffer);
            chunkSize = getChunkSize(line);
            if (chunkSize == 0) {
                checkpoint(State.READ_CHUNK_FOOTER);
                return null;
            } else {
                checkpoint(State.READ_CHUNKED_CONTENT);
            }
        }
        case READ_CHUNKED_CONTENT: {
            readChunkedContent(channel, buffer);
        }
        case READ_CHUNK_DELIMITER: {
            // FIXME: LF should be skipped, too. (check the whole codec)
            byte next = buffer.readByte();
            if (next == HttpCodecUtil.CR) {
                buffer.readByte();
            }
            checkpoint(State.READ_CHUNK_SIZE);
            return null;
        }
        case READ_CHUNK_FOOTER: {
            String line = readIntoCurrentLine(buffer);
            if (line.trim().length() == 0) {
                return reset();
            } else {
                checkpoint(State.READ_CHUNK_FOOTER);
                return null;
            }
        }
        default: {
            throw new Error("Shouldn't reach here.");
        }

        }
    }

    private Object reset() {
        message.setContent(content);
        content = null;
        checkpoint(State.SKIP_CONTROL_CHARS);
        return message;
    }

    private void skipControlCharacters(ChannelBuffer buffer) {
        for (;;) {
            char c = (char) buffer.readUnsignedByte();
            if (!Character.isISOControl(c) &&
                !Character.isWhitespace(c)) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                break;
            }
        }
    }

    private void readChunkedContent(Channel channel, ChannelBuffer buffer) {
        if (content == null) {
            content = ChannelBuffers.dynamicBuffer(
                    chunkSize, channel.getConfig().getBufferFactory());
        }
        content.writeBytes(buffer, chunkSize);
        checkpoint(State.READ_CHUNK_DELIMITER);
    }

    private void readFixedLengthContent(ChannelBuffer buffer) {
        int length = message.getContentLength();
        if (content == null) {
            content = buffer.readBytes(length);
        } else {
            content.writeBytes(buffer.readBytes(length));
        }
    }

    private void readHeaders(ChannelBuffer buffer) {
        message.clearHeaders();
        String line = readIntoCurrentLine(buffer);
        String lastHeader = null;
        while (line.length() != 0) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                List<String> current = message.getHeaders(lastHeader);
                int lastPos = current.size() - 1;
                String newString = current.get(lastPos) + line.trim();
                current.remove(lastPos);
                current.add(newString);
            }
            else {
                String[] header = splitHeader(line);
                message.addHeader(header[0], header[1]);
                lastHeader = header[0];
            }
            line = readIntoCurrentLine(buffer);
        }

        State nextState;
        if (message.isChunked()) {
            nextState = State.READ_CHUNK_SIZE;
        } else if (message.getContentLength() >= 0) {
            nextState = State.READ_FIXED_LENGTH_CONTENT;
        } else {
            nextState = State.READ_CONTENT;
        }
        checkpoint(nextState);
    }

    protected abstract void readInitial(ChannelBuffer buffer) throws Exception;

    private int getChunkSize(String hex) {
        hex = hex.trim();
        for (int i = 0; i < hex.length(); i ++) {
            char c = hex.charAt(i);
            if (c == ';' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                hex = hex.substring(0, i);
                break;
            }
        }

        return Integer.parseInt(hex, 16);
    }

    protected String readIntoCurrentLine(ChannelBuffer channel) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            byte nextByte = channel.readByte();
            if (nextByte == HttpCodecUtil.CR) {
                nextByte = channel.readByte();
                if (nextByte == HttpCodecUtil.LF) {
                    return sb.toString();
                }
            }
            else if (nextByte == HttpCodecUtil.LF) {
                return sb.toString();
            }
            else {
                sb.append((char) nextByte);
            }
        }
    }

    protected String[] splitInitial(String sb) {
        Matcher m = INITIAL_PATTERN.matcher(sb);
        if (m.matches()) {
            return new String[] { m.group(1), m.group(2), m.group(3) };
        } else {
            throw new IllegalArgumentException("Invalid initial line: " + sb);
        }
    }

    private String[] splitHeader(String sb) {
        Matcher m = HEADER_PATTERN.matcher(sb);
        if (m.matches()) {
            return new String[] { m.group(1), m.group(2) };
        } else {
            throw new IllegalArgumentException("Invalid header syntax: " + sb);
        }
    }
}
