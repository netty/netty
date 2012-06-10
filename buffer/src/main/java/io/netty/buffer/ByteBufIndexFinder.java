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
 * Locates an index of data in a {@link ByteBuf}.
 * <p>
 * This interface enables the sequential search for the data which meets more
 * complex and dynamic condition than just a simple value matching.  Please
 * refer to {@link ByteBuf#indexOf(int, int, ByteBufIndexFinder)} and
 * {@link ByteBuf#bytesBefore(int, int, ByteBufIndexFinder)}
 * for more explanation.
 * @apiviz.uses io.netty.buffer.ChannelBuffer
 */
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
     * Index finder which locates a {@code NUL (0x00)} byte.
     */
    ByteBufIndexFinder NUL = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == 0;
        }
    };

    /**
     * Index finder which locates a non-{@code NUL (0x00)} byte.
     */
    ByteBufIndexFinder NOT_NUL = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != 0;
        }
    };

    /**
     * Index finder which locates a {@code CR ('\r')} byte.
     */
    ByteBufIndexFinder CR = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == '\r';
        }
    };

    /**
     * Index finder which locates a non-{@code CR ('\r')} byte.
     */
    ByteBufIndexFinder NOT_CR = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != '\r';
        }
    };

    /**
     * Index finder which locates a {@code LF ('\n')} byte.
     */
    ByteBufIndexFinder LF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) == '\n';
        }
    };

    /**
     * Index finder which locates a non-{@code LF ('\n')} byte.
     */
    ByteBufIndexFinder NOT_LF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            return buffer.getByte(guessedIndex) != '\n';
        }
    };

    /**
     * Index finder which locates a {@code CR ('\r')} or {@code LF ('\n')}.
     */
    ByteBufIndexFinder CRLF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b == '\r' || b == '\n';
        }
    };

    /**
     * Index finder which locates a byte which is neither a {@code CR ('\r')}
     * nor a {@code LF ('\n')}.
     */
    ByteBufIndexFinder NOT_CRLF = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b != '\r' && b != '\n';
        }
    };

    /**
     * Index finder which locates a linear whitespace
     * ({@code ' '} and {@code '\t'}).
     */
    ByteBufIndexFinder LINEAR_WHITESPACE = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b == ' ' || b == '\t';
        }
    };

    /**
     * Index finder which locates a byte which is not a linear whitespace
     * (neither {@code ' '} nor {@code '\t'}).
     */
    ByteBufIndexFinder NOT_LINEAR_WHITESPACE = new ByteBufIndexFinder() {
        @Override
        public boolean find(ByteBuf buffer, int guessedIndex) {
            byte b = buffer.getByte(guessedIndex);
            return b != ' ' && b != '\t';
        }
    };
}
