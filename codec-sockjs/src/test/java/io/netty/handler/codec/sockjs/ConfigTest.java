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
package io.netty.handler.codec.sockjs;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class ConfigTest {

    @Test
    public void websocketProtocols() {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").websocketProtocols("one", "two").build();
        assertThat(config.websocketProtocol().size(), is(2));
        assertThat(config.websocketProtocol(), hasItems("one", "two"));
    }

    @Test
    public void websocketProtocolsAsCSV() {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").websocketProtocols("one", "two").build();
        assertThat(config.websocketProtocol().size(), is(2));
        assertThat(config.websocketProtocolCSV(), containsString("one"));
        assertThat(config.websocketProtocolCSV(), containsString("two"));
    }

    @Test
    public void websocketProtocolsAsCSVNoProtocols() {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        assertThat(config.websocketProtocol().size(), is(0));
        assertThat(config.websocketProtocolCSV(), is(nullValue()));
    }

}
