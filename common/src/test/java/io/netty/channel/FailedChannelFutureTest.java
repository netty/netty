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
package io.netty.channel;

import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

public class FailedChannelFutureTest {
    @Test
    public void testConstantProperties() {
        Channel channel = createMock(Channel.class);
        Exception e = new Exception();
        FailedChannelFuture future = new FailedChannelFuture(channel, null, e);

        assertFalse(future.isSuccess());
        assertSame(e, future.cause());
    }

    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullException() {
        new FailedChannelFuture(createMock(Channel.class), null, null);
    }
}
