/*
 * Copyright 2019 The Netty Project
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
package io.netty5.channel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.netty5.channel.ChannelHandler.Sharable;

public class ChannelHandlerAdapterTest {

    @Sharable
    private static final class SharableChannelHandlerAdapter extends ChannelHandlerAdapter {
    }

    @Test
    public void testSharable() {
        ChannelHandlerAdapter handler = new SharableChannelHandlerAdapter();
        assertTrue(handler.isSharable());
    }

    @Test
    public void testInnerClassSharable() {
        ChannelHandlerAdapter handler = new @Sharable ChannelHandlerAdapter() { };
        assertTrue(handler.isSharable());
    }

    @Test
    public void testWithoutSharable() {
        ChannelHandlerAdapter handler = new ChannelHandlerAdapter() { };
        assertFalse(handler.isSharable());
    }
}
