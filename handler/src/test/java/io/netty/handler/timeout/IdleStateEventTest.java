/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.timeout;

import org.junit.Test;

import static io.netty.handler.timeout.IdleStateEvent.*;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.Assert.*;

public class IdleStateEventTest {
    @Test
    public void testHumanReadableToString() {
        assertThat(FIRST_READER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(READER_IDLE, first)"));
        assertThat(READER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(READER_IDLE)"));
        assertThat(FIRST_WRITER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(WRITER_IDLE, first)"));
        assertThat(WRITER_IDLE_STATE_EVENT, hasToString("IdleStateEvent(WRITER_IDLE)"));
        assertThat(FIRST_ALL_IDLE_STATE_EVENT, hasToString("IdleStateEvent(ALL_IDLE, first)"));
        assertThat(ALL_IDLE_STATE_EVENT, hasToString("IdleStateEvent(ALL_IDLE)"));
    }
}
