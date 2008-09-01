/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
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
package org.jboss.netty.buffer;


/**
 * Locates an index of data in {@link ChannelBuffer}.
 * <p>
 * This interface enables the sequential search for the data which meets more
 * complex and dynamic condition than just a simple value matching.  Please
 * refer to {@link ChannelBuffer#indexOf(int, int, ChannelBufferIndexFinder)},
 * {@link ChannelBuffer#readBytes(ChannelBufferIndexFinder)},
 * {@link ChannelBuffer#readSlice(ChannelBufferIndexFinder)}, and
 * {@link ChannelBuffer#skipBytes(ChannelBufferIndexFinder)}
 * for more explanation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
