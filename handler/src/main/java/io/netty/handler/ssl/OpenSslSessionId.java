/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.util.internal.EmptyArrays;

import java.util.Arrays;

/**
 * Represent the session ID used by an {@link OpenSslSession}.
 */
final class OpenSslSessionId {

    private final byte[] id;
    private final int hashCode;

    static final OpenSslSessionId NULL_ID = new OpenSslSessionId(EmptyArrays.EMPTY_BYTES);

    OpenSslSessionId(byte[] id) {
        // We take ownership if the byte[] and so there is no need to clone it.
        this.id = id;
        // cache the hashCode as the byte[] array will never change
        this.hashCode = Arrays.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenSslSessionId)) {
            return false;
        }

        return Arrays.equals(id, ((OpenSslSessionId) o).id);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    byte[] cloneBytes() {
        return id.clone();
    }
}
