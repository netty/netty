/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.smtp;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.UnstableApi;

/**
 * Default implementation of {@link LastSmtpContent} that does no validation of the raw data passed in.
 */
@UnstableApi
public final class DefaultLastSmtpContent extends DefaultSmtpContent implements LastSmtpContent {

    /**
     * Creates a new instance using the given data.
     */
    public DefaultLastSmtpContent(ByteBuf data) {
        super(data);
    }

    @Override
    public LastSmtpContent copy() {
        return (LastSmtpContent) super.copy();
    }

    @Override
    public LastSmtpContent duplicate() {
        return (LastSmtpContent) super.duplicate();
    }

    @Override
    public LastSmtpContent retainedDuplicate() {
        return (LastSmtpContent) super.retainedDuplicate();
    }

    @Override
    public LastSmtpContent replace(ByteBuf content) {
        return new DefaultLastSmtpContent(content);
    }

    @Override
    public DefaultLastSmtpContent retain() {
        super.retain();
        return this;
    }

    @Override
    public DefaultLastSmtpContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DefaultLastSmtpContent touch() {
        super.touch();
        return this;
    }

    @Override
    public DefaultLastSmtpContent touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
