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

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.sockjs.util.HttpRequestBuilder;
import org.junit.Test;

import static io.netty.handler.codec.sockjs.util.SockJsAsserts.assertWelcomeMessage;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

public class GreetingTest {

    @Test
    public void greeting() throws Exception {
        final FullHttpResponse response = sendGreetingRequest();
        assertWelcomeMessage(response);
        response.release();
    }

    @Test
    public void matchesNull() {
        assertThat(Greeting.matches(null), is(false));
    }

    @Test
    public void matchesEmpty() {
        assertThat(Greeting.matches(""), is(true));
    }

    @Test
    public void matchesSlash() {
        assertThat(Greeting.matches("/"), is(true));
    }

    @Test
    public void matchesNullString() {
        assertThat(Greeting.matches("null"), is(false));
    }

    private static FullHttpResponse sendGreetingRequest() {
        return Greeting.response(HttpRequestBuilder.getRequest("/simplepush").build());
    }

}
