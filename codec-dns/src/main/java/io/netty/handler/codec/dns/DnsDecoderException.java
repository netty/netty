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
package io.netty.handler.codec.dns;

import io.netty.handler.codec.DecoderException;

/**
 * {@link DecoderException} which can be thrown during parsing of a DNS query or response,
 * which indicates the DNS error code that should be returned.
 */
public class DnsDecoderException extends DecoderException {

    private final DnsResponseCode code;

    DnsDecoderException(DnsResponseCode code) {
        super(code.toString());
        if (code == null) {
            throw new NullPointerException("code");
        }
        this.code = code;
    }

    DnsDecoderException(DnsResponseCode code, Throwable cause) {
        super(code.toString(), cause);
        if (code == null) {
            throw new NullPointerException("code");
        }
        this.code = code;
    }

    DnsDecoderException(DnsResponseCode code, String message, Throwable cause) {
        super(message, cause);
        if (code == null) {
            throw new NullPointerException("code");
        }
        this.code = code;
    }

    DnsDecoderException(DnsResponseCode code, String message) {
        super(message);
        if (code == null) {
            throw new NullPointerException("code");
        }
        this.code = code;
    }

    public DnsResponseCode code() {
        return code;
    }
}
