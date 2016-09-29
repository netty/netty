/*
 * Copyright 2016 The Netty Project
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
/**
 * show issue https://github.com/netty/netty/issues/5182
 * HostsFileParser tries to resolve hostnames as case-sensitive
 */
package io.netty.resolver;

import org.junit.Assert;
import org.junit.Test;

public class DefaultHostsFileEntriesResolverTest {

    @Test
    public void testCaseInsensitivity() throws Exception {
        DefaultHostsFileEntriesResolver resolver = new DefaultHostsFileEntriesResolver();
        //normalized somehow
        Assert.assertEquals(resolver.normalize("localhost"), resolver.normalize("LOCALHOST"));
    }
}
