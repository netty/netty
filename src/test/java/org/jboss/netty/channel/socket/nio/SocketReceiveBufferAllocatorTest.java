/*
 * Copyright 2012 The Netty Project
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

package org.jboss.netty.channel.socket.nio;

import static org.junit.Assert.*;

import org.junit.Test;

public class SocketReceiveBufferAllocatorTest {

    @Test
    public void testDecreaseBuffer() {
        SocketReceiveBufferAllocator allocator = new SocketReceiveBufferAllocator(2, 80);
        assertEquals(8192, allocator.get(8192).capacity());
        assertEquals(8192, allocator.get(1024).capacity());
        
        // Not 80% of the capacity so should just return the old buffer
        assertEquals(8192, allocator.get(8000).capacity());

        assertEquals(8192, allocator.get(1024).capacity());
        assertEquals(1024, allocator.get(1024).capacity());

        allocator.releaseExternalResources();
    }

}
