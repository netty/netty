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

package io.netty.buffer;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class PoolArenaTest {

    @Test
    public void testNormalizeCapacity() throws Exception {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, 0, 0, 9, 999999);
        int[] reqCapacities = {0, 15, 510, 1024, 1023, 1025};
        int[] expectedResult = {0, 16, 512, 1024, 1024, 2048};
        for (int i = 0; i < reqCapacities.length; i ++) {
            Assert.assertEquals(expectedResult[i], arena.normalizeCapacity(reqCapacities[i]));
        }
    }
}
