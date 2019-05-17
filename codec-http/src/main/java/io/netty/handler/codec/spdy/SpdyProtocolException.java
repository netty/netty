/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;

public class SpdyProtocolException extends Exception {

    private static final long serialVersionUID = 7870000537743847264L;

    /**
     * Creates a new instance.
     */
    public SpdyProtocolException() { }

    /**
     * Creates a new instance.
     */
    public SpdyProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public SpdyProtocolException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public SpdyProtocolException(Throwable cause) {
        super(cause);
    }

    static SpdyProtocolException newStatic(String message) {
        if (PlatformDependent.javaVersion() >= 7) {
            return new SpdyProtocolException(message, true);
        }
        return new SpdyProtocolException(message);
    }

    @SuppressJava6Requirement(reason = "uses Java 7+ Exception.<init>(String, Throwable, boolean, boolean)" +
            " but is guarded by version checks")
    private SpdyProtocolException(String message, boolean shared) {
        super(message, null, false, true);
        assert shared;
    }
}
