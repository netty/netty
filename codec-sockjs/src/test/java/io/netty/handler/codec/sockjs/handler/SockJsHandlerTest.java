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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import io.netty.handler.codec.sockjs.transports.Transports;

import org.junit.Test;

public class SockJsHandlerTest {

    @Test
    public void nonMatch() {
        final SockJsHandler.PathParams sessionPath = SockJsHandler.matches("/xhr_send");
        assertThat(sessionPath.matches(), is(false));
    }

    @Test
    public void matches() {
        final SockJsHandler.PathParams sessionPath = SockJsHandler.matches("/000/123/xhr_send");
        assertThat(sessionPath.matches(), is(true));
        assertThat(sessionPath.serverId(), equalTo("000"));
        assertThat(sessionPath.sessionId(), equalTo("123"));
        assertThat(sessionPath.transport(), equalTo(Transports.Type.XHR_SEND));
    }

}
