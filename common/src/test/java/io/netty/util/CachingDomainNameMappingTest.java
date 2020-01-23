/*
 * Copyright 2020 The Netty Project
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
package io.netty.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CachingDomainNameMappingTest {
    private CachingDomainNameMapping<Object> mapping;
    private static final Object defaultValue = new Object();
    private static final Object valueExactMatch1 = new Object();
    private static final Object valueExactMatch2 = new Object();
    private static final Object valueWildCard1 = new Object();
    private static final Object valueWildCard2 = new Object();

    @Before
    public void setup() {
        final CachingDomainNameMapping.Builder<Object> builder =
                new CachingDomainNameMapping.Builder<Object>(defaultValue);
        builder.add("g.netty.com", valueExactMatch1);
        builder.add("*.netty.io", valueWildCard1);
        builder.add("s.netty.com", valueExactMatch2);
        builder.add("*.s.netty.com", valueWildCard2);
        mapping = builder.build();
    }

    @Test
    public void testDefaultValue() {
        Assert.assertEquals(defaultValue, mapping.map("not-mapped.com"));
    }

    @Test
    public void testExactDomainNameMatch() {
        Assert.assertEquals(valueExactMatch1, mapping.map("g.netty.com"));
        Assert.assertEquals(valueExactMatch2, mapping.map("s.netty.com"));
        Assert.assertNotEquals(valueExactMatch1, "d.netty.com");
        Assert.assertNotEquals(valueExactMatch2, "abc.s.netty.com");
    }

    @Test
    public void testWildcardDomainNameMatch() {
        Assert.assertEquals(valueWildCard1, mapping.map("abc.netty.io"));
        Assert.assertEquals(valueWildCard2, mapping.map("efg.s.netty.com"));
    }
}
