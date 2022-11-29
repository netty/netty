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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.security.core.payload.PayloadMatcher;
import io.netty.security.core.payload.RegexPayload;
import io.netty.security.core.standards.StandardPayloadMatcher;
import io.netty.security.core.standards.StandardRegexPayload;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardRegexPayloadTest {

    @Test
    void createRegexStringPayloadAndMatch() {
        ByteBuf completeSentence = ByteBufUtil.writeAscii(ByteBufAllocator.DEFAULT, "1");

        RegexPayload regexPayload = StandardRegexPayload.create(Pattern.compile("\\d"));
        PayloadMatcher<Object, Object> payloadMatcher = new StandardPayloadMatcher();
        assertTrue(payloadMatcher.validate(regexPayload, completeSentence));
    }
}
