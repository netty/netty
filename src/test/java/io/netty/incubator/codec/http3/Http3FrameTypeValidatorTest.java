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
package io.netty.incubator.codec.http3;

import org.junit.Assert;
import org.junit.Test;

public abstract class Http3FrameTypeValidatorTest {

    protected abstract long[] invalidFramesTypes();
    protected abstract long[] validFrameTypes();

    protected abstract Http3FrameTypeValidator newValidator();

    @Test
    public void testValidFrameTypes() throws Exception {
        for (long validFrameType: validFrameTypes()) {
            newValidator().validate(validFrameType, true);
        }
    }

    @Test
    public void testInvalidFrameTypes() {
        for (long invalidFrameType: invalidFramesTypes()) {
            try {
                newValidator().validate(invalidFrameType, true);
                Assert.fail("Expected failure for frame type: " + invalidFrameType);
            } catch (Http3Exception expected) {
                // ignore
            }
        }
    }
}
