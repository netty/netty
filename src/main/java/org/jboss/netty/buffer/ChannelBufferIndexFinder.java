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
package org.jboss.netty.buffer;


/**
 * Locates an index of data in a {@link ChannelBuffer}.
 * <p>
 * This interface enables the sequential search for the data which meets more
 * complex and dynamic condition than just a simple value matching.  Please
 * refer to {@link ChannelBuffer#indexOf(int, int, ChannelBufferIndexFinder)} and
 * {@link ChannelBuffer#bytesBefore(int, int, ChannelBufferIndexFinder)}
 * for more explanation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 * @apiviz.uses org.jboss.netty.buffer.ChannelBuffer
 */
public interface ChannelBufferIndexFinder {

    /**
     * Returns {@code true} if and only if the data is found at the specified
     * {@code guessedIndex} of the specified {@code buffer}.
     * <p>
     * The implementation should not perform an operation which raises an
     * exception such as {@link IndexOutOfBoundsException} nor perform
     * an operation which modifies the content of the buffer.
     */
    boolean find(ChannelBuffer buffer, int guessedIndex);

    /**
     * Index finder which locates a {@code NUL (0x00)} byte.
     */
    static ChannelBufferIndexFinder NUL = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == 0;
        }
    };

    /**
     * Index finder which locates a non-{@code NUL (0x00)} byte.
     */
    static ChannelBufferIndexFinder NOT_NUL = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != 0;
        }
    };

    /**
     * Index finder which locates a {@code CR ('\r')} byte.
     */
    static ChannelBufferIndexFinder CR = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == '\r';
        }
    };

    /**
     * Index finder which locates a non-{@code CR ('\r')} byte.
     */
    static ChannelBufferIndexFinder NOT_CR = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != '\r';
        }
    };

    /**
     * Index finder which locates a {@code LF ('\n')} byte.
     */
    static ChannelBufferIndexFinder LF = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == '\n';
        }
    };

    /**
     * Index finder which locates a non-{@code LF ('\n')} byte.
     */
    static ChannelBufferIndexFinder NOT_LF = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != '\n';
        }
    };

    /**
     * Index finder which locates a {@code CR ('\r')} or {@code LF ('\n')}.
     */
    static ChannelBufferIndexFinder CRLF = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b == '\r' || b == '\n';
        }
    };

    /**
     * Index finder which locates a byte which is neither a {@code CR ('\r')}
     * nor a {@code LF ('\n')}.
     */
    static ChannelBufferIndexFinder NOT_CRLF = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b != '\r' && b != '\n';
        }
    };

    /**
     * Index finder which locates a linear whitespace
     * ({@code ' '} and {@code '\t'}).
     */
    static ChannelBufferIndexFinder LINEAR_WHITESPACE = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b == ' ' || b == '\t';
        }
    };

    /**
     * Index finder which locates a byte which is not a linear whitespace
     * (neither {@code ' '} nor {@code '\t'}).
     */
    static ChannelBufferIndexFinder NOT_LINEAR_WHITESPACE = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b != ' ' && b != '\t';
        }
    };
}
