/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.sockjs.handlers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

import org.junit.Test;

public class CorsMetadataTest {

    @Test
    public void nullObjects() {
        final CorsMetadata md = new CorsMetadata(null, null);
        assertThat(md.origin(), equalTo("*"));
        assertThat(md.headers(), is(nullValue()));
        assertThat(md.hasHeaders(), is(false));
    }

    @Test
    public void nullOriginString() {
        final CorsMetadata md = new CorsMetadata("null", null);
        assertThat(md.origin(), equalTo("*"));
        assertThat(md.headers(), is(nullValue()));
        assertThat(md.hasHeaders(), is(false));
    }

}
