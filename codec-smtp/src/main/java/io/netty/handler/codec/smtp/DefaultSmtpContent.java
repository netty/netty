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
package io.netty.handler.codec.smtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.internal.UnstableApi;

/**
 * Default implementation of {@link SmtpContent} that does no validation of the raw data passed in.
 */
@UnstableApi
public class DefaultSmtpContent extends DefaultByteBufHolder implements SmtpContent {

    /**
     * Creates a new instance using the given data.
     */
    public DefaultSmtpContent(ByteBuf data) {
        super(data);
    }

    @Override
    public SmtpContent copy() {
        return (SmtpContent) super.copy();
    }

    @Override
    public SmtpContent duplicate() {
        return (SmtpContent) super.duplicate();
    }

    @Override
    public SmtpContent retainedDuplicate() {
        return (SmtpContent) super.retainedDuplicate();
    }

    @Override
    public SmtpContent replace(ByteBuf content) {
        return new DefaultSmtpContent(content);
    }

    @Override
    public SmtpContent retain() {
        super.retain();
        return this;
    }

    @Override
    public SmtpContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public SmtpContent touch() {
        super.touch();
        return this;
    }

    @Override
    public SmtpContent touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
