/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.unix.FileDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringFileTest {

    private static EventLoopGroup group;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        group =  new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
    }

    @Test
    public void testGetFd() throws IOException {
        File file = File.createTempFile("temp", ".tmp");
        file.deleteOnExit();
        FileChannel channel = FileChannel.open(file.toPath());
        int fd = Native.getFd(channel);
        Assertions.assertTrue(fd > 0);
    }

    @Test
    public void testAsyncSplice() throws Exception {
        String sampleString = "hello netty io_uring sendFile!";
        File inFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
        inFile.deleteOnExit();
        File outFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
        outFile.deleteOnExit();
        Files.write(inFile.toPath(), sampleString.getBytes());

        try (
                FileChannel inFileChannel = FileChannel.open(inFile.toPath(), StandardOpenOption.READ);
                FileChannel outFileChannel = FileChannel.open(outFile.toPath(), StandardOpenOption.WRITE)
        ) {
            IoUringSendFile sendFileHandle = IoUringSendFile.newInstance(group.next())
                    .sync().getNow();
            Integer now = sendFileHandle.sendFile(
                    new FileDescriptor(Native.getFd(inFileChannel)), 0,
                    new FileDescriptor(Native.getFd(outFileChannel)), 0, sampleString.length(), 0
            ).sync().getNow();

            Assertions.assertEquals(sampleString.length(), now.intValue());

            byte[] bytes = Files.readAllBytes(outFile.toPath());
            Assertions.assertArrayEquals(sampleString.getBytes(), bytes);
        }
    }

    @AfterAll
    public static void closeResource() throws InterruptedException {
        group.shutdownGracefully().sync();
    }
}
