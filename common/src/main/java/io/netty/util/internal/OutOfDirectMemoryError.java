/*
 * Copyright 2016 The Netty Project
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

import java.nio.ByteBuffer;

/**
 * {@link OutOfMemoryError} that is throws if {@link PlatformDependent#allocateDirectNoCleaner(int)} can not allocate
 * a new {@link ByteBuffer} due memory restrictions.
 */
public final class OutOfDirectMemoryError extends OutOfMemoryError {
    private static final long serialVersionUID = 4228264016184011555L;

    OutOfDirectMemoryError(String s) {
        super(s);
    }
}
