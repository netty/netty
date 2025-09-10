/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

public class Bzip2IntegrationTest extends AbstractIntegrationTest {

    @Override
    protected EmbeddedChannel createEncoder() {
        return new EmbeddedChannel(new Bzip2Encoder());
    }

    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new Bzip2Decoder());
    }

    @Test
    public void test3Tables() throws Exception {
        byte[] data = new byte[500];
        rand.nextBytes(data);
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void test4Tables() throws Exception {
        byte[] data = new byte[1100];
        rand.nextBytes(data);
        testIdentity(data, true);
        testIdentity(data, false);
    }

    @Test
    public void test5Tables() throws Exception {
        byte[] data = new byte[2300];
        rand.nextBytes(data);
        testIdentity(data, true);
        testIdentity(data, false);
    }
}
