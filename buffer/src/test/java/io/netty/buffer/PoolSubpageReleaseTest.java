package io.netty.buffer;

import org.junit.platform.commons.util.ReflectionUtils;

import static org.junit.Assert.assertEquals;

public class PoolSubpageReleaseTest {
    public static void main(String[] args) throws Exception {
        // int elemSize = 1234;
        // int elemSize = 4096;
        int elemSize = 8192;
        // int elemSize = 5000;
        // int elemSize = 10000;
        // int elemSize = 28672;
        run(elemSize);
    }

    private static void run(int elemSize) {
        int oneLength = 128, fixLength = 256;
        ByteBuf[] ones = new ByteBuf[oneLength];
        ByteBuf[] fixByteBuf = new ByteBuf[fixLength];

        //allocate
        for (int i = 0; i < fixLength; i++) {
            fixByteBuf[i] = ByteBufAllocator.DEFAULT.buffer(elemSize, elemSize);
        }
        // by reflection acquire chunk.poolSubpages
        PoolSubpage<Object>[] subpages = byReflectionGetSubpages(fixByteBuf[0]);

        // fixSubpagesCount ==  when ( PoolThreadCache.MemoryRegionCache.queue.offer(entry) == false ), the chunk.poolSubpages.size
        int fixSubpagesCount = countSubpages(subpages);

        for (int i = 0; i < oneLength; i++) {
            ones[i] = ByteBufAllocator.DEFAULT.buffer(elemSize, elemSize);
        }

        //first release the 'tows', in oder to filled of PoolThreadCache.MemoryRegionCache.queue, ( PoolThreadCache.MemoryRegionCache.queue.offer(entry) == false )
        for (int i = 0; i < fixLength; i++) {
            fixByteBuf[i].release();
        }
        // release the 'ones'
        for (int i = 0; i < oneLength; i++) {
            ones[i].release();
            System.out.println("subpages size: " + countSubpages(subpages));
        }
        // after release all but in PoolThreadCache.MemoryRegionCache.queue,  chunk.poolSubpages.size == fixSubpagesCount + 1
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
            return (PoolSubpage[]) ReflectionUtils.tryToReadFieldValue(PoolChunk.class.getDeclaredField("subpages"), chunk).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PoolSubpage[0];
    }
}