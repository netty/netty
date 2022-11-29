/*
 * Copyright 2022 The Netty Project
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
package io.netty.contrib.security.core.standards;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.security.core.payload.HexPayload;
import io.netty.security.core.payload.PayloadMatcher;
import io.netty.security.core.standards.StandardHexPayload;
import io.netty.security.core.standards.StandardPayloadMatcher;
import org.junit.jupiter.api.Test;

import static io.netty.security.core.Util.hexStringToByteArray;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardHexPayloadTest {

    @Test
    void createHexStringPayloadAndMatch() {
        ByteBuf completeSentence = Unpooled.wrappedBuffer(hexStringToByteArray("4865792C2049276D206E6F74206120636174"));

        HexPayload hexPayload = StandardHexPayload.create("636174"); // cat
        PayloadMatcher<Object, Object> payloadMatcher = new StandardPayloadMatcher();
        assertTrue(payloadMatcher.validate(hexPayload, completeSentence));
    }
}
