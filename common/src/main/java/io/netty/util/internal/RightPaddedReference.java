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

package io.netty.util.internal;

import java.util.concurrent.atomic.AtomicReference;

public final class RightPaddedReference<T> extends AtomicReference<T> {
    private static final long serialVersionUID = -467619563034125237L;

    // cache line padding (must be public)
    public transient long rp1, rp2, rp3, rp4, rp5; // 40 bytes (excluding AtomicReference.value and object header)
    public transient long rpA, rpB, rpC, rpD, rpE, rpF, rpG, rpH; // 64 bytes

    public RightPaddedReference() { }

    public RightPaddedReference(T initialValue) {
        super(initialValue);
    }
}
