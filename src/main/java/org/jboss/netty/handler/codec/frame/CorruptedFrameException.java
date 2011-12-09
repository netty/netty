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
package org.jboss.netty.handler.codec.frame;

/**
 * An {@link Exception} which is thrown when the received frame data can not
 * be decoded by a {@link FrameDecoder} implementation.
 *
 * @apiviz.hidden
 */
public class CorruptedFrameException extends Exception {

    private static final long serialVersionUID = 3918052232492988408L;

    /**
     * Creates a new instance.
     */
    public CorruptedFrameException() {
        super();
    }

    /**
     * Creates a new instance.
     */
    public CorruptedFrameException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public CorruptedFrameException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public CorruptedFrameException(Throwable cause) {
        super(cause);
    }
}
