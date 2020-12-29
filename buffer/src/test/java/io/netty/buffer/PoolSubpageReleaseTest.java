/*
 * Copyright 2020 The Netty Project
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

package io.netty.buffer;

import org.junit.Test;
import org.junit.platform.commons.util.ReflectionUtils;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

public class PoolSubpageReleaseTest {
    public PoolSubpageReleaseTest() {
    }

    @Test
    public void testAbnormalPoolSubpageRelease() {
        int elemSize = 8192;
        run(elemSize);
    }

    @Test
    public void testNormalPoolSubpageRelease() {
        // 0 < eleSize <= 28672, != 8192
        int elemSize = 1234;
        run(elemSize);
    }

    private void run(int elemSize) {
        int oneLength = 128, fixLength = 256;
        ByteBuf[] ones = new ByteBuf[oneLength];
        ByteBuf[] fixByteBuf = new ByteBuf[fixLength];

        //allocate
        for (int i = 0; i < fixLength; i++) {
            fixByteBuf[i] = ByteBufAllocator.DEFAULT.buffer(elemSize, elemSize);
        }
        // by reflection acquire chunk.poolSubpages
        PoolSubpage<Object>[] subpages = byReflectionGetSubpages(fixByteBuf[0]);

        // fixSubpagesCount ==  when ( PoolThreadCache.MemoryRegionCache.queue.offer(entry) == false )
        // the chunk.poolSubpages.size
        int fixSubpagesCount = countSubpages(subpages);

        for (int i = 0; i < oneLength; i++) {
            ones[i] = ByteBufAllocator.DEFAULT.buffer(elemSize, elemSize);
        }

        //first release the 'tows', in oder to filled of PoolThreadCache.MemoryRegionCache.queue
        // ( PoolThreadCache.MemoryRegionCache.queue.offer(entry) == false )
        for (int i = 0; i < fixLength; i++) {
            fixByteBuf[i].release();
        }
        // release the 'ones'
        for (int i = 0; i < oneLength; i++) {
            ones[i].release();
            // System.out.println("subpages size: " + countSubpages(subpages));
        }
        // after release all but in PoolThreadCache.MemoryRegionCache.queue
        // chunk.poolSubpages.size == fixSubpagesCount + 1
        assertEquals(countSubpages(subpages), fixSubpagesCount + 1);
    }

    private static int countSubpages(PoolSubpage<Object>[] subpages) {
        int num = 0;
        for (final PoolSubpage<Object> subpage : subpages) {
            if (null != subpage) {
                num++;
            }
        }
        return num;
    }

    private static PoolSubpage[] byReflectionGetSubpages(ByteBuf one) {
        Object chunk;
        try {
            chunk = ReflectionUtils.tryToReadFieldValue(PooledByteBuf.class.getDeclaredField("chunk"), one).get();
            Field subpagesField = PoolChunk.class.getDeclaredField("subpages");
            return (PoolSubpage[]) ReflectionUtils.tryToReadFieldValue(subpagesField, chunk).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PoolSubpage[0];
    }
}
