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
package io.netty.handler.codec.spdy;

import io.netty.util.internal.ObjectUtil;

/**
 * The SPDY session status code and its description.
 */
public class SpdySessionStatus implements Comparable<SpdySessionStatus> {

    /**
     * 0 OK
     */
    public static final SpdySessionStatus OK =
        new SpdySessionStatus(0, "OK");

    /**
     * 1 Protocol Error
     */
    public static final SpdySessionStatus PROTOCOL_ERROR =
        new SpdySessionStatus(1, "PROTOCOL_ERROR");

    /**
     * 2 Internal Error
     */
    public static final SpdySessionStatus INTERNAL_ERROR =
        new SpdySessionStatus(2, "INTERNAL_ERROR");

    /**
     * Returns the {@link SpdySessionStatus} represented by the specified code.
     * If the specified code is a defined SPDY status code, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static SpdySessionStatus valueOf(int code) {
        switch (code) {
        case 0:
            return OK;
        case 1:
            return PROTOCOL_ERROR;
        case 2:
            return INTERNAL_ERROR;
        }

        return new SpdySessionStatus(code, "UNKNOWN (" + code + ')');
    }

    private final int code;

    private final String statusPhrase;

    /**
     * Creates a new instance with the specified {@code code} and its
     * {@code statusPhrase}.
     */
    public SpdySessionStatus(int code, String statusPhrase) {
        this.statusPhrase = ObjectUtil.checkNotNull(statusPhrase, "statusPhrase");
        this.code = code;
    }

    /**
     * Returns the code of this status.
     */
    public int code() {
        return code;
    }

    /**
     * Returns the status phrase of this status.
     */
    public String statusPhrase() {
        return statusPhrase;
    }

    @Override
    public int hashCode() {
        return code();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SpdySessionStatus)) {
            return false;
        }

        return code() == ((SpdySessionStatus) o).code();
    }

    @Override
    public String toString() {
        return statusPhrase();
    }

    @Override
    public int compareTo(SpdySessionStatus o) {
        return code() - o.code();
    }
}
