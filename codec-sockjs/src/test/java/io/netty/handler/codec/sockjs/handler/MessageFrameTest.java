/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.handler;

import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import static org.junit.Assert.assertThat;

public class MessageFrameTest {

    @Test
    public void messages() {
        final String[] messages = {"one", "two", "three"};
        final MessageFrame messageFrame = new MessageFrame(messages);
        assertThat(messageFrame.messages().size(), CoreMatchers.is(3));
        assertThat(messageFrame.messages(), CoreMatchers.hasItems("one", "two", "three"));
        messageFrame.release();
    }

    @Test
    public void createWithNullTerminatedArray() {
        final String[] nullTerminated = {"one", "two", null, "three"};
        final MessageFrame messageFrame = new MessageFrame(nullTerminated);
        assertThat(messageFrame.messages().size(), CoreMatchers.is(2));
        assertThat(messageFrame.messages(), CoreMatchers.hasItems("one", "two"));
        messageFrame.release();
    }

}
