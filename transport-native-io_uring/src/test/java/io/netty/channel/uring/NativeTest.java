/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.File;
import sun.misc.SharedSecrets;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledUnsafeDirectByteBuf;
import static org.junit.Assert.*;

public class NativeTest {

    @Test
    public void test_io_uring() {
        long uring = Native.ioUringSetup(32);

        long fd = Native.createFile();
        System.out.println("Fd: " + fd);

        ByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        UnpooledUnsafeDirectByteBuf directByteBufPooled = new UnpooledUnsafeDirectByteBuf(allocator, 500, 1000);

        System.out.println("MemoryAddress: " + directByteBufPooled.hasMemoryAddress());
        String inputString = "Hello World!";
        byte[] byteArrray = inputString.getBytes();
        directByteBufPooled.writeBytes(byteArrray);

        Native.ioUringWrite(uring, fd, 1, directByteBufPooled.memoryAddress(), directByteBufPooled.readerIndex(),
                directByteBufPooled.writerIndex());

        Native.ioUringSubmit(uring);

        long cqe = Native.ioUringWaitCqe(uring);

        // ystem.out.println("Res: " + Native.ioUringGetRes(cqe));
        assertEquals(12, Native.ioUringGetRes(cqe));

        Native.ioUringClose(uring);
    }
}
