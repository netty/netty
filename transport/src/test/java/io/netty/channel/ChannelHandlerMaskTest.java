/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel;

import org.junit.jupiter.api.Test;

import static io.netty.channel.ChannelHandlerMask.MASK_ONLY_INBOUND;
import static io.netty.channel.ChannelHandlerMask.MASK_ONLY_OUTBOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelHandlerMaskTest {
    private static final int SMALLEST_VALID_MASK = 1;
    private static final int GREATEST_VALID_MASK = MASK_ONLY_INBOUND | MASK_ONLY_OUTBOUND;

    @Test
    void indexOfMustFindGivenMask() {
        for (int mask = SMALLEST_VALID_MASK; mask <= GREATEST_VALID_MASK; mask++) {
            try {
                indexOfMustFindGivenMask(mask);
            } catch (Throwable e) {
                e.addSuppressed(new AssertionError("Failed for mask " + mask + " 0x" + Integer.toHexString(mask)));
                throw e;
            }
        }
    }

    private static void indexOfMustFindGivenMask(int mask) {
        int notThisMask = mask == 1 ? 2 : 1;
        long inMasks;
        inMasks = ChannelHandlerMask.packInboundMasks(mask, 0, 0);
        assertEquals(0, ChannelHandlerMask.packedMaskIndexOf(mask, inMasks));
        inMasks = ChannelHandlerMask.packInboundMasks(0, mask, 0);
        assertEquals(1, ChannelHandlerMask.packedMaskIndexOf(mask, inMasks));
        inMasks = ChannelHandlerMask.packInboundMasks(0, 0, mask);
        assertEquals(2, ChannelHandlerMask.packedMaskIndexOf(mask, inMasks));

        inMasks = ChannelHandlerMask.packInboundMasks(mask, notThisMask, notThisMask);
        assertEquals(0, ChannelHandlerMask.packedMaskIndexOf(mask, inMasks));
        inMasks = ChannelHandlerMask.packInboundMasks(notThisMask, mask, notThisMask);
        assertEquals(1, ChannelHandlerMask.packedMaskIndexOf(mask, inMasks));
        inMasks = ChannelHandlerMask.packInboundMasks(notThisMask, notThisMask, mask);
        assertEquals(2, ChannelHandlerMask.packedMaskIndexOf(mask, inMasks));

        inMasks = ChannelHandlerMask.packInboundMasks(mask, 0, 0);
        assertEquals(3, ChannelHandlerMask.packedMaskIndexOf(notThisMask, inMasks));
        inMasks = ChannelHandlerMask.packInboundMasks(0, mask, 0);
        assertEquals(3, ChannelHandlerMask.packedMaskIndexOf(notThisMask, inMasks));
        inMasks = ChannelHandlerMask.packInboundMasks(0, 0, mask);
        assertEquals(3, ChannelHandlerMask.packedMaskIndexOf(notThisMask, inMasks));

        inMasks = ChannelHandlerMask.packOutboundMasks(mask, 0);
        assertEquals(3, ChannelHandlerMask.packedMaskIndexOf(notThisMask, inMasks));
        inMasks = ChannelHandlerMask.packOutboundMasks(0, mask);
        assertEquals(3, ChannelHandlerMask.packedMaskIndexOf(notThisMask, inMasks));
    }

    @Test
    void storingAndRetreivingOutboundContexts() throws Exception {
        AbstractChannelHandlerContext[] cache = ChannelHandlerMask.initContextCache();

        DefaultChannelHandlerContext ctx1 = new DefaultChannelHandlerContext(
                null, null, "", new ChannelInboundHandlerAdapter());
        DefaultChannelHandlerContext ctx2 = new DefaultChannelHandlerContext(
                null, null, "", new ChannelInboundHandlerAdapter());

        long masks = 0;
        int index;

        masks = ChannelHandlerMask.storeOutbound(cache, masks, ctx1, ChannelHandlerMask.MASK_CLOSE);
        index = ChannelHandlerMask.packedMaskIndexOf(ChannelHandlerMask.MASK_CLOSE, masks);
        assertTrue(ChannelHandlerMask.packedMaskIndexFound(index));
        assertSame(ctx1, ChannelHandlerMask.getFoundOutbound(cache, index));

        masks = ChannelHandlerMask.storeOutbound(cache, masks, ctx2, ChannelHandlerMask.MASK_READ);
        index = ChannelHandlerMask.packedMaskIndexOf(ChannelHandlerMask.MASK_READ, masks);
        assertTrue(ChannelHandlerMask.packedMaskIndexFound(index));
        assertSame(ctx2, ChannelHandlerMask.getFoundOutbound(cache, index));

        index = ChannelHandlerMask.packedMaskIndexOf(ChannelHandlerMask.MASK_CLOSE, masks);
        assertTrue(ChannelHandlerMask.packedMaskIndexFound(index));
        assertSame(ctx1, ChannelHandlerMask.getFoundOutbound(cache, index));
    }

    @Test
    void storingAndRetreivingInboundContexts() throws Exception {
        AbstractChannelHandlerContext[] cache = ChannelHandlerMask.initContextCache();

        DefaultChannelHandlerContext ctx1 = new DefaultChannelHandlerContext(
                null, null, "", new ChannelInboundHandlerAdapter());
        DefaultChannelHandlerContext ctx2 = new DefaultChannelHandlerContext(
                null, null, "", new ChannelInboundHandlerAdapter());
        DefaultChannelHandlerContext ctx3 = new DefaultChannelHandlerContext(
                null, null, "", new ChannelInboundHandlerAdapter());

        long masks = 0;
        int index;

        masks = ChannelHandlerMask.storeInbound(cache, masks, ctx1, ChannelHandlerMask.MASK_CHANNEL_ACTIVE);
        index = ChannelHandlerMask.packedMaskIndexOf(ChannelHandlerMask.MASK_CHANNEL_ACTIVE, masks);
        assertTrue(ChannelHandlerMask.packedMaskIndexFound(index));
        assertSame(ctx1, ChannelHandlerMask.getFoundInbound(cache, index));

        masks = ChannelHandlerMask.storeInbound(cache, masks, ctx2, ChannelHandlerMask.MASK_CHANNEL_READ);
        index = ChannelHandlerMask.packedMaskIndexOf(ChannelHandlerMask.MASK_CHANNEL_READ, masks);
        assertTrue(ChannelHandlerMask.packedMaskIndexFound(index));
        assertSame(ctx2, ChannelHandlerMask.getFoundInbound(cache, index));

        masks = ChannelHandlerMask.storeInbound(cache, masks, ctx3, ChannelHandlerMask.MASK_CHANNEL_READ_COMPLETE);
        index = ChannelHandlerMask.packedMaskIndexOf(ChannelHandlerMask.MASK_CHANNEL_READ_COMPLETE, masks);
        assertTrue(ChannelHandlerMask.packedMaskIndexFound(index));
        assertSame(ctx3, ChannelHandlerMask.getFoundInbound(cache, index));

        index = ChannelHandlerMask.packedMaskIndexOf(ChannelHandlerMask.MASK_CHANNEL_ACTIVE, masks);
        assertTrue(ChannelHandlerMask.packedMaskIndexFound(index));
        assertSame(ctx1, ChannelHandlerMask.getFoundInbound(cache, index));
    }
}
