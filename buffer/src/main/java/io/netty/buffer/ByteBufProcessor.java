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

import io.netty.util.ByteProcessor;

/**
 * @deprecated Use {@link ByteProcessor}.
 */
@Deprecated
public interface ByteBufProcessor extends ByteProcessor {

    /**
     * @deprecated Use {@link ByteProcessor#FIND_NUL}.
     */
    @Deprecated
    ByteBufProcessor FIND_NUL = value -> value != 0;

    /**
     * @deprecated Use {@link ByteProcessor#FIND_NON_NUL}.
     */
    @Deprecated
    ByteBufProcessor FIND_NON_NUL = value -> value == 0;

    /**
     * @deprecated Use {@link ByteProcessor#FIND_CR}.
     */
    @Deprecated
    ByteBufProcessor FIND_CR = value -> value != '\r';

    /**
     * @deprecated Use {@link ByteProcessor#FIND_NON_CR}.
     */
    @Deprecated
    ByteBufProcessor FIND_NON_CR = value -> value == '\r';

    /**
     * @deprecated Use {@link ByteProcessor#FIND_LF}.
     */
    @Deprecated
    ByteBufProcessor FIND_LF = value -> value != '\n';

    /**
     * @deprecated Use {@link ByteProcessor#FIND_NON_LF}.
     */
    @Deprecated
    ByteBufProcessor FIND_NON_LF = value -> value == '\n';

    /**
     * @deprecated Use {@link ByteProcessor#FIND_CRLF}.
     */
    @Deprecated
    ByteBufProcessor FIND_CRLF = value -> value != '\r' && value != '\n';

    /**
     * @deprecated Use {@link ByteProcessor#FIND_NON_CRLF}.
     */
    @Deprecated
    ByteBufProcessor FIND_NON_CRLF = value -> value == '\r' || value == '\n';

    /**
     * @deprecated Use {@link ByteProcessor#FIND_LINEAR_WHITESPACE}.
     */
    @Deprecated
    ByteBufProcessor FIND_LINEAR_WHITESPACE = value -> value != ' ' && value != '\t';

    /**
     * @deprecated Use {@link ByteProcessor#FIND_NON_LINEAR_WHITESPACE}.
     */
    @Deprecated
    ByteBufProcessor FIND_NON_LINEAR_WHITESPACE = value -> value == ' ' || value == '\t';
}
