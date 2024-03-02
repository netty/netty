/*
 * Copyright 2017 The Netty Project
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

import io.netty5.channel.ReadHandleFactory.ReadHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class AdaptiveReadHandleFactoryTest {

    private ReadHandle handle;

    @BeforeEach
    public void setup() {
        AdaptiveReadHandleFactory recvBufferAllocator = new AdaptiveReadHandleFactory(2, 64, 512, 1024 * 1024 * 10);
        handle = recvBufferAllocator.newHandle(mock(Channel.class));
    }

    @Test
    public void rampUpBeforeReadCompleteWhenLargeDataPending() {
        // Simulate that there is always more data when we attempt to read, so we should always ramp up.
        allocReadExpected(handle, 512);
        handle.readComplete();
        allocReadExpected(handle, 8192);
        handle.readComplete();
        allocReadExpected(handle, 131072);
        handle.readComplete();
        allocReadExpected(handle, 2097152);
        handle.readComplete();

        allocReadExpected(handle, 8388608);
    }

    @Test
    void mustNotLetMoreThanMaxMessagesBePrepared() {
        allocReadExpected(handle, 512);
        allocReadExpected(handle, 8192);
        allocReadExpected(handle, 0);
        handle.readComplete();
        allocReadExpected(handle, 65536);
    }

    @Test
    public void memoryAllocationIntervalsTest() {
        computingNext(512, 512);
        computingNext(8192, 1110);
        computingNext(4096, 1200);
        computingNext(4096, 1300);
        computingNext(2048, 1500);
        computingNext(2048, 1700);
        computingNext(2048, 1550);
        computingNext(2048, 2000);
        computingNext(2048, 1900);
    }

    private void computingNext(int expectedSize, int actualReadBytes) {
        assertEquals(expectedSize, handle.prepareRead());
        handle.lastRead(expectedSize, actualReadBytes, 1);
        handle.readComplete();
    }

    @Test
    public void lastPartialReadDoesNotRampDown() {
        allocReadExpected(handle, 512);
        // Simulate there is just 1 byte remaining which is unread. However, the total bytes in the current read
        // cycle means that we should stay at the current step for the next ready cycle.
        allocRead(handle, 8192, 1);
        handle.readComplete();

        allocReadExpected(handle, 8192);
    }

    @Test
    public void lastPartialReadCanRampUp() {
        allocReadExpected(handle, 512);
        // We simulate there is just 1 less byte than we try to read, but because of the adaptive steps the total
        // amount of bytes read for this read cycle steps up to prepare for the next read cycle.
        allocRead(handle, 8192, 8191);
        handle.readComplete();

        allocReadExpected(handle, 131072);
    }

    @Test
    public void doesNotExceedMaximum() {
        AdaptiveReadHandleFactory recvBufferAllocator = new AdaptiveReadHandleFactory(2, 64, 9000, 9000);
        handle = recvBufferAllocator.newHandle(mock(Channel.class));
        allocReadExpected(handle, 8192);
    }

    @Test
    public void doesSetCorrectMinBounds() {
        AdaptiveReadHandleFactory recvBufferAllocator = new AdaptiveReadHandleFactory(2, 81, 95, 95);
        handle = recvBufferAllocator.newHandle(mock(Channel.class));

        allocReadExpected(handle, 81);
    }

    @Test
    public void throwsIfInitialIsBiggerThenMaximum() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new AdaptiveReadHandleFactory(2, 64, 4096 , 1024);
            }
        });
    }

    @Test
    public void throwsIfInitialIsSmallerThenMinimum() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new AdaptiveReadHandleFactory(2, 512, 64 , 1024);
            }
        });
    }

    @Test
    public void throwsIfMinimumIsBiggerThenMaximum() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new AdaptiveReadHandleFactory(2, 2048, 64 , 1024);
            }
        });
    }

    private static void allocReadExpected(ReadHandle handle, int expectedSize) {
        allocRead(handle, expectedSize, expectedSize);
    }

    private static void allocRead(ReadHandle handle, int expectedBufferSize, int lastRead) {
        assertEquals(expectedBufferSize, handle.prepareRead());
        handle.lastRead(expectedBufferSize, lastRead, 1);
    }
}
