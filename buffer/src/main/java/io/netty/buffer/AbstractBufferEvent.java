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
package io.netty.buffer;

import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.MemoryAddress;

@SuppressWarnings("Since15")
abstract class AbstractBufferEvent extends AbstractAllocatorEvent {
    @DataAmount
    @Description("Configured buffer capacity")
    public int size;
    @DataAmount
    @Description("Actual allocated buffer capacity")
    public int maxFastCapacity;
    @DataAmount
    @Description("Maximum buffer capacity")
    public int maxCapacity;
    @Description("Is this buffer referencing off-heap memory?")
    public boolean direct;
    @Description("The memory address of the off-heap memory, if available")
    @MemoryAddress
    public long address;

    public void fill(AbstractByteBuf buf, AllocatorType allocatorType) {
        this.allocatorType = allocatorType;
        size = buf.capacity();
        maxFastCapacity = buf.maxFastWritableBytes() + buf.writerIndex();
        maxCapacity = buf.maxCapacity();
        direct = buf.isDirect();
        address = buf._memoryAddress();
    }
}
