/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2267 $, $Date: 2010-05-06 16:00:52 +0900 (Thu, 06 May 2010) $
 */
public class StaticChannelPipelineTest {

    @Test
    public void testConstructionWithoutNull() {
        StaticChannelPipeline p = new StaticChannelPipeline(new A(), new B());
        Map<String, ChannelHandler> m = p.toMap();
        Assert.assertEquals(2, m.size());
        Assert.assertTrue(m.get("0") instanceof A);
        Assert.assertTrue(m.get("1") instanceof B);
    }

    @Test
    public void testConstructionWithNull1() {
        StaticChannelPipeline p = new StaticChannelPipeline(null, new A(), new B());
        Map<String, ChannelHandler> m = p.toMap();
        Assert.assertEquals(0, m.size());

    }

    @Test
    public void testConstructionWithNull2() {
        StaticChannelPipeline p = new StaticChannelPipeline(new A(), null, new B());
        Map<String, ChannelHandler> m = p.toMap();
        Assert.assertEquals(1, m.size());
        Assert.assertTrue(m.get("0") instanceof A);

    }

    @Test
    public void testConstructionWithNull() {
        StaticChannelPipeline p = new StaticChannelPipeline(new A(), new B(), null);
        Map<String, ChannelHandler> m = p.toMap();
        Assert.assertEquals(2, m.size());
        Assert.assertTrue(m.get("0") instanceof A);
        Assert.assertTrue(m.get("1") instanceof B);
    }

    static final class A extends SimpleChannelHandler {
        // Dummy
    }

    static final class B extends SimpleChannelHandler {
        // Dummy
    }
}
