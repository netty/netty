/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http.cookie;

import static io.netty.handler.codec.http.cookie.CookieUtil.firstInvalidCookieNameOctet;
import static io.netty.handler.codec.http.cookie.CookieUtil.firstInvalidCookieValueOctet;
import static io.netty.handler.codec.http.cookie.CookieUtil.unwrapValue;

import java.nio.CharBuffer;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Parent of Client and Server side cookie decoders
 */
public abstract class CookieDecoder {

    private final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());

    private final boolean strict;
    private final CookieErrorHandler cookieErrorHandler;

    protected CookieDecoder(boolean strict) {
        this.strict = strict;
        cookieErrorHandler = new CookieErrorHandler() {
            @Override
            public void handle(String message, Object... arguments) {
                logger.debug(String.format(message, arguments));
            }
        };
    }

    protected CookieDecoder(boolean strict, CookieErrorHandler cookieErrorHandler) {
        this.strict = strict;
        this.cookieErrorHandler = cookieErrorHandler;
    }

    protected DefaultCookie initCookie(String header, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
        if (nameBegin == -1 || nameBegin == nameEnd) {
            cookieErrorHandler.handle("Skipping cookie with null name");
            return null;
        }

        if (valueBegin == -1) {
            cookieErrorHandler.handle("Skipping cookie with null value");
            return null;
        }

        CharSequence wrappedValue = CharBuffer.wrap(header, valueBegin, valueEnd);
        CharSequence unwrappedValue = unwrapValue(wrappedValue);
        if (unwrappedValue == null) {
            cookieErrorHandler.handle("Skipping cookie because starting quotes are not properly balanced" +
                    " in '%s'", wrappedValue);
            return null;
        }

        final String name = header.substring(nameBegin, nameEnd);

        int invalidOctetPos;
        if (strict && (invalidOctetPos = firstInvalidCookieNameOctet(name)) >= 0) {
            cookieErrorHandler.handle("Skipping cookie because name '%s' contains invalid char '%s'",
                    name, name.charAt(invalidOctetPos));
            return null;
        }

        final boolean wrap = unwrappedValue.length() != valueEnd - valueBegin;

        if (strict && (invalidOctetPos = firstInvalidCookieValueOctet(unwrappedValue)) >= 0) {
            if (logger.isDebugEnabled()) {
                cookieErrorHandler.handle("Skipping cookie because value '%s' contains invalid char '%s'",
                        unwrappedValue, unwrappedValue.charAt(invalidOctetPos));
            }
            return null;
        }

        DefaultCookie cookie = new DefaultCookie(name, unwrappedValue.toString());
        cookie.setWrap(wrap);
        return cookie;
    }

    public interface CookieErrorHandler {
       void handle(String message, Object... arguments);
    }
}
