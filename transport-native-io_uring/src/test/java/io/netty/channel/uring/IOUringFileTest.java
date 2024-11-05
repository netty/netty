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

import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.io.File;

public class IOUringFileTest {

    @Test
    public void testOpenAndClose() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(IoUringIoHandler.newFactory());
        File f = File.createTempFile("io_uring_test", ".tmp");
        IoUringFile file = IoUringFile.open(group.next(), f.getAbsolutePath(), 0, 644).sync().getNow();
        file.close().sync();
    }
}
