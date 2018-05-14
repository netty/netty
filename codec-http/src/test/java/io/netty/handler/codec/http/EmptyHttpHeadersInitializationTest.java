/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A test to validate that either order of initialization of the {@link EmptyHttpHeaders#INSTANCE} and
 * {@link HttpHeaders#EMPTY_HEADERS} field results in both fields being non-null.
 *
 * Since this is testing static initialization, the tests might not actually test anything, except
 * when run in isolation.
 */
public class EmptyHttpHeadersInitializationTest {

    @Test
    public void testEmptyHttpHeadersFirst() {
        assertNotNull(EmptyHttpHeaders.INSTANCE);
        assertNotNull(HttpHeaders.EMPTY_HEADERS);
    }

    @Test
    public void testHttpHeadersFirst() {
        assertNotNull(HttpHeaders.EMPTY_HEADERS);
        assertNotNull(EmptyHttpHeaders.INSTANCE);
    }

}
