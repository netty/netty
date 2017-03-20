/*
 * Copyright 2014 The Netty Project
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
package io.netty.buffer;

import com.google.common.base.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ByteBufUtilLengthOfBomTest {

    private final Charset charset;
    private final int expectedBomLength;
    private final boolean injectBom;

    public ByteBufUtilLengthOfBomTest(Charset charset, int expectedBomLength, boolean injectBom) {
        this.charset = charset;
        this.expectedBomLength = expectedBomLength;
        this.injectBom = injectBom;
    }

    @Test
    public void lengthOfBom() throws IOException {
        final byte[] bytes = ((injectBom ? ByteBufUtil.BOM_STR : "") + "Hello there").getBytes(charset);

        ByteBuf buf = Unpooled.copiedBuffer(bytes);
        try {
            int length = ByteBufUtil.lengthOfByteOrderMark(buf, charset);
            assertThat(length, equalTo(expectedBomLength));
        } finally {
            buf.release();
        }
    }

    @Parameterized.Parameters(name = "{index}: charset = {0}, expectedBomLength = {1}, injectBom = {2}")
    public static Collection<Object> data() {
        List<Object> params = new ArrayList<Object>();
        params.add(new Object[]{Charsets.UTF_8, 3, true});
        params.add(new Object[]{Charsets.UTF_16BE, 2, true});
        params.add(new Object[]{Charsets.UTF_16LE, 2, true});
        params.add(new Object[]{Charsets.UTF_8, 0, false});
        params.add(new Object[]{Charsets.UTF_16BE, 0, false});
        params.add(new Object[]{Charsets.UTF_16LE, 0, false});
        return params;
    }

}
