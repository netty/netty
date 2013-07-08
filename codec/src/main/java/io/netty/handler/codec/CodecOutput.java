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

package io.netty.handler.codec;

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;

import java.util.ArrayList;

/**
 * A simple list that holds the output of a codec.
 */
final class CodecOutput extends ArrayList<Object> {

    private static final long serialVersionUID = -8605125654176467947L;

    private static final int DEFAULT_INITIAL_CAPACITY = 8;

    private static final Recycler<CodecOutput> RECYCLER = new Recycler<CodecOutput>() {
        @Override
        protected CodecOutput newObject(Handle handle) {
            return new CodecOutput(handle);
        }
    };

    /**
     * Create a new empty {@link CodecOutput} instance
     */
    public static CodecOutput newInstance() {
        return newInstance(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Create a new empty {@link CodecOutput} instance with the given capacity.
     */
    public static CodecOutput newInstance(int minCapacity) {
        CodecOutput ret = (CodecOutput) RECYCLER.get();
        ret.ensureCapacity(minCapacity);
        return ret;
    }

    private final Handle handle;

    CodecOutput(Handle handle) {
        this(handle, DEFAULT_INITIAL_CAPACITY);
    }

    CodecOutput(Handle handle, int initialCapacity) {
        super(initialCapacity);
        this.handle = handle;
    }

    /**
     * Clear and recycle this instance.
     */
    boolean recycle() {
        clear();
        return RECYCLER.recycle(this, handle);
    }
}
