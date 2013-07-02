/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.Signal;

public interface ByteBufProcessor {

    Signal ABORT = new Signal(ByteBufProcessor.class.getName() + ".ABORT");

    /**
     * Aborts on a {@code NUL (0x00)}.
     */
    ByteBufProcessor FIND_NUL = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value == 0) {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a non-{@code NUL (0x00)}.
     */
    ByteBufProcessor FIND_NON_NUL = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value != 0) {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a {@code CR ('\r')}.
     */
    ByteBufProcessor FIND_CR = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value == '\r') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a non-{@code CR ('\r')}.
     */
    ByteBufProcessor FIND_NON_CR = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value != '\r') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a {@code LF ('\n')}.
     */
    ByteBufProcessor FIND_LF = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value == '\n') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a non-{@code LF ('\n')}.
     */
    ByteBufProcessor FIND_NON_LF = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value != '\n') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a {@code CR ('\r')} or a {@code LF ('\n')}.
     */
    ByteBufProcessor FIND_CRLF = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value == '\r' || value == '\n') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a byte which is neither a {@code CR ('\r')} nor a {@code LF ('\n')}.
     */
    ByteBufProcessor FIND_NON_CRLF = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value != '\r' && value != '\n') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a linear whitespace (a ({@code ' '} or a {@code '\t'}).
     */
    ByteBufProcessor FIND_LINEAR_WHITESPACE = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value == ' ' || value == '\t') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * Aborts on a byte which is not a linear whitespace (neither {@code ' '} nor {@code '\t'}).
     */
    ByteBufProcessor FIND_NON_LINEAR_WHITESPACE = new ByteBufProcessor() {
        @Override
        public int process(byte value) throws Exception {
            if (value != ' ' && value != '\t') {
                throw ABORT;
            } else {
                return 1;
            }
        }
    };

    /**
     * @return the number of elements processed. {@link ByteBuf#forEachByte(ByteBufProcessor)} will determine
     *         the index of the next byte to be processed based on this value.  Usually, an implementation will
     *         return {@code 1} to advance the index by {@code 1}.  Note that returning a non-positive value is
     *         allowed where a negative value advances the index in the opposite direction and zero leaves the index
     *         as-is.
     */
    int process(byte value) throws Exception;
}
