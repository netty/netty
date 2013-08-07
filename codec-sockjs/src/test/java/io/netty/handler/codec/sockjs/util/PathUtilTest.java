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
package io.netty.handler.codec.sockjs.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Test;

public class PathUtilTest {

    @Test
    public void forService() {
        final String url = "http://localhost:8000/echo";
        assertThat(PathUtil.forService(url, "echo"), is(true));
    }

    @Test
    public void forServiceWithSubpaths() {
        final String url = "http://localhost:8000/a/b/c/d/echo";
        assertThat(PathUtil.forService(url, "echo"), is(true));
    }

    @Test
    public void forServiceNull() {
        assertThat(PathUtil.forService(null, "echo"), is(false));
    }

    @Test
    public void forServiceWithQueryParam() {
        final String url = "http://localhost:8000/a/b/c/d/echo?name=Fletch";
        assertThat(PathUtil.forService(url, "echo"), is(true));
    }

    @Test
    public void prefix() {
        assertThat(PathUtil.prefix("http://localhost:8000/a/b/c/d/echo?name=Fletch"), equalTo("echo"));
        assertThat(PathUtil.prefix("http://localhost:8000/a/b/c/d/echo/info"), equalTo("info"));
    }

}
