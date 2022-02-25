/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Send;
import io.netty5.handler.codec.DecoderResult;

import java.util.Objects;

public final class EmptyLastHttpContent implements LastHttpContent<EmptyLastHttpContent> {
    private final Buffer payload;
    private final BufferAllocator allocator;

    public EmptyLastHttpContent(BufferAllocator allocator) {
        payload = allocator.allocate(0);
        this.allocator = allocator;
    }

    @Override
    public Buffer payload() {
        return payload;
    }

    @Override
    public Send<EmptyLastHttpContent> send() {
        return Send.sending(EmptyLastHttpContent.class, () -> new EmptyLastHttpContent(allocator));
    }

    @Override
    public void close() {
        payload.close();
    }

    @Override
    public boolean isAccessible() {
        return payload.isAccessible();
    }

    @Override
    public EmptyLastHttpContent touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return EmptyHttpHeaders.INSTANCE;
    }

    @Override
    public DecoderResult decoderResult() {
        return DecoderResult.SUCCESS;
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public String toString() {
        return "EmptyLastHttpContent";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EmptyLastHttpContent that = (EmptyLastHttpContent) o;

        return Objects.equals(payload, that.payload) && Objects.equals(allocator, that.allocator);
    }

    @Override
    public int hashCode() {
        int result = payload != null ? payload.hashCode() : 0;
        result = 31 * result + (allocator != null ? allocator.hashCode() : 0);
        return result;
    }
}
