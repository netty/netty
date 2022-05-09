/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.tests.adaptor;

import io.netty5.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class NioByteBufAdaptorTest extends ByteBufAdaptorTest {
    static ByteBufAllocatorAdaptor alloc;

    @BeforeAll
    public static void setUpAllocator() {
        alloc = setUpAllocator("ByteBuffer");
    }

    @AfterAll
    public static void tearDownAllocator() throws Exception {
        if (alloc != null) {
            alloc.close();
        }
    }

    @Override
    protected ByteBufAllocatorAdaptor alloc() {
        return alloc;
    }
}
