/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * Attribute interface
 */
public interface Attribute extends HttpData {
    /**
     * Returns the value of this HttpData.
     */
    String getValue() throws IOException;

    /**
     * Sets the value of this HttpData.
     */
    void setValue(String value) throws IOException;

    @Override
    Attribute copy();

    @Override
    Attribute duplicate();

    @Override
    Attribute retainedDuplicate();

    @Override
    Attribute replace(ByteBuf content);

    @Override
    Attribute retain();

    @Override
    Attribute retain(int increment);

    @Override
    Attribute touch();

    @Override
    Attribute touch(Object hint);
}
