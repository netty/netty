/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl.ocsp;

import java.util.Date;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

public class OcspResponse {
    private final Status status;
    private final Date thisUpdate;
    private final Date nextUpdate;

    public OcspResponse(Status status, Date thisUpdate, Date nextUpdate) {
        this.status = checkNotNull(status, "Status");
        this.thisUpdate = checkNotNull(thisUpdate, "ThisUpdate");
        this.nextUpdate = nextUpdate; // The 'nextUpdate' field is optional.
    }

    /**
     * The OCSP response status, saying if the certificate is valid or not.
     */
    public Status status() {
        return status;
    }

    /**
     * The date of the given OCSP update, never {@code null}.
     */
    public Date thisUpdate() {
        return thisUpdate;
    }

    /**
     * The future date of the next OCSP update, if any.
     * This field is optional, and if {@code null}, indicates that an update is available at any time.
     */
    public Date nextUpdate() {
        return nextUpdate;
    }

    @Override
    public String toString() {
        return "OcspResponse{" +
                "status=" + status +
                ", thisUpdate=" + thisUpdate +
                ", nextUpdate=" + nextUpdate +
                '}';
    }

    public enum Status {
        /**
         * Certificate is valid
         */
        VALID,

        /**
         * Certificate is revoked
         */
        REVOKED,

        /**
         * Certificate status is unknown
         */
        UNKNOWN
    }
}
