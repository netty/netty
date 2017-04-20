/*
 * Copyright 2014 The Netty Project
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

import io.netty.handler.codec.sockjs.handler.SockJsPaths.SockJsPath;
import io.netty.handler.codec.sockjs.transport.TransportType;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class SockJsPathsTest {

    @Test
    public void invalidPath() {
        final SockJsPath sessionPath = SockJsPaths.parse("/xhr_send");
        assertThat(sessionPath.isValid(), is(false));
    }

    @Test
    public void validPath() {
        final SockJsPath sessionPath = SockJsPaths.parse("/000/123/xhr_send");
        assertThat(sessionPath.isValid(), is(true));
        assertThat(sessionPath.serverId(), equalTo("000"));
        assertThat(sessionPath.sessionId(), equalTo("123"));
        assertThat(sessionPath.transport(), equalTo(TransportType.XHR_SEND));
    }

}
