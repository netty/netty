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
package io.netty.buffer;


/**
 * @deprecated Use {@link ByteBufProcessor} instead.
 *
 * Locates an index of data in a {@link ByteBuf}.
 * <p>
 * This interface enables the sequential search for the data which meets more
 * complex and dynamic condition than just a simple value matching.  Please
 * refer to {@link ByteBuf#indexOf(int, int, ByteBufIndexFinder)} and
 * {@link ByteBuf#bytesBefore(int, int, ByteBufIndexFinder)}
 * for more explanation.
 */
@Deprecated
public interface ByteBufIndexFinder {

    /**
     * Returns {@code true} if and only if the data is found at the specified
     * {@code guessedIndex} of the specified {@code buffer}.
     * <p>
     * The implementation should not perform an operation which raises an
     * exception such as {@link IndexOutOfBoundsException} nor perform
     * an operation which modifies the content of the buffer.
     */
    boolean find(ByteBuf buffer, int guessedIndex);

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_NUL} instead.
     *
     * Index finder which locates a {@code NUL (0x00)} byte.
     */
    @Deprecated
    ByteBufIndexFinder NUL = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == 0;
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_NON_NUL} instead.
     *
     * Index finder which locates a non-{@code NUL (0x00)} byte.
     */
    @Deprecated
    ByteBufIndexFinder NOT_NUL = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != 0;
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_CR} instead.
     *
     * Index finder which locates a {@code CR ('\r')} byte.
     */
    @Deprecated
    ByteBufIndexFinder CR = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == '\r';
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_NON_CR} instead.
     *
     * Index finder which locates a non-{@code CR ('\r')} byte.
     */
    @Deprecated
    ByteBufIndexFinder NOT_CR = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != '\r';
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_LF} instead.
     *
     * Index finder which locates a {@code LF ('\n')} byte.
     */
    @Deprecated
    ByteBufIndexFinder LF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == '\n';
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_NON_LF} instead.
     *
     * Index finder which locates a non-{@code LF ('\n')} byte.
     */
    @Deprecated
    ByteBufIndexFinder NOT_LF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != '\n';
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_CRLF} instead.
     *
     * Index finder which locates a {@code CR ('\r')} or {@code LF ('\n')}.
     */
    @Deprecated
    ByteBufIndexFinder CRLF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b == '\r' || b == '\n';
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_NON_CRLF} instead.
     *
     * Index finder which locates a byte which is neither a {@code CR ('\r')} nor a {@code LF ('\n')}.
     */
    @Deprecated
    ByteBufIndexFinder NOT_CRLF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b != '\r' && b != '\n';
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_LINEAR_WHITESPACE} instead.
     *
     * Index finder which locates a linear whitespace ({@code ' '} and {@code '\t'}).
     */
    @Deprecated
    ByteBufIndexFinder LINEAR_WHITESPACE = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b == ' ' || b == '\t';
        }
    };

    /**
     * @deprecated Use {@link ByteBufProcessor#FIND_NON_LINEAR_WHITESPACE} instead.
     *
     * Index finder which locates a byte which is not a linear whitespace (neither {@code ' '} nor {@code '\t'}).
     */
    @Deprecated
    ByteBufIndexFinder NOT_LINEAR_WHITESPACE = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b != ' ' && b != '\t';
        }
    };
}
