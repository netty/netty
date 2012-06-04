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
package io.netty.handler.codec.serialization;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class CompactObjectSerializationTest {

    @Test
    public void testInterfaceSerialization() throws Exception {
        PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn = new PipedInputStream(pipeOut);
        CompactObjectOutputStream out = new CompactObjectOutputStream(pipeOut);
        CompactObjectInputStream in = new CompactObjectInputStream(pipeIn, ClassResolvers.cacheDisabled(null));
        out.writeObject(List.class);
        Assert.assertSame(List.class, in.readObject());
    }
}
