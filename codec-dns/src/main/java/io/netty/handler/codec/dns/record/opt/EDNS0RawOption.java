/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns.record.opt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

/**
 * EDNS0RawOption is a option that hold the raw content, This option will only be used if the code is not supported.
 */
public class EDNS0RawOption implements EDNS0Option, ByteBufHolder {
    private final EDNS0OptionCode optionCode;
    private final ByteBuf content;

    public EDNS0RawOption(EDNS0OptionCode optionCode, ByteBuf content) {
        this.optionCode = optionCode;
        this.content = content;
    }

    @Override
    public EDNS0OptionCode optionCode() {
        return null;
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public ByteBufHolder copy() {
        return replace(content().copy());
    }

    @Override
    public ByteBufHolder duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public ByteBufHolder retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public ByteBufHolder replace(ByteBuf content) {
        return new EDNS0RawOption(optionCode, content);
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public ByteBufHolder retain() {
        content().retain();
        return this;
    }

    @Override
    public ByteBufHolder retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public ByteBufHolder touch() {
        content().touch();
        return this;
    }

    @Override
    public ByteBufHolder touch(Object hint) {
        content().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content().release();
    }

    @Override
    public boolean release(int decrement) {
        return content().release(decrement);
    }
}
