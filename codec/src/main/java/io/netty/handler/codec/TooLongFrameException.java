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

/**
 * An {@link DecoderException} which is thrown when the length of the frame
 * decoded is greater than the allowed maximum.
 */
public class TooLongFrameException extends DecoderException {

    private static final long serialVersionUID = -1995801950698951640L;

    /**
     * Creates a new instance.
     */
    public TooLongFrameException() {
    }

    /**
     * Creates a new instance.
     */
    public TooLongFrameException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public TooLongFrameException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public TooLongFrameException(Throwable cause) {
        super(cause);
    }
}
