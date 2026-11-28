/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProtocolIdTest {

    @Test
    public void testValueOfReturnsTheConstants() {
        assertThat(ProtocolId.valueOf("http/1.0")).isSameAs(ProtocolId.HTTP_1_0);
        assertThat(ProtocolId.valueOf("http/1.1")).isSameAs(ProtocolId.HTTP_1_1);
        assertThat(ProtocolId.valueOf("h2")).isSameAs(ProtocolId.H2);
        assertThat(ProtocolId.valueOf("h2c")).isSameAs(ProtocolId.H2C);
        assertThat(ProtocolId.valueOf("h3")).isSameAs(ProtocolId.H3);
    }

    @Test
    public void testDecodingReturnsTheConstants() {
        assertThat(decode("http/1.0")).isSameAs(ProtocolId.HTTP_1_0);
        assertThat(decode("http/1.1")).isSameAs(ProtocolId.HTTP_1_1);
        assertThat(decode("h2")).isSameAs(ProtocolId.H2);
        assertThat(decode("h2c")).isSameAs(ProtocolId.H2C);
        assertThat(decode("h3")).isSameAs(ProtocolId.H3);
    }

    @Test
    public void testOtherIdsAreEqualByName() {
        ProtocolId decoded = decode("h3-29");

        assertThat(decoded).isEqualTo(ProtocolId.valueOf("h3-29")).hasSameHashCodeAs(ProtocolId.valueOf("h3-29"))
                .isNotEqualTo(ProtocolId.H3);
        assertThat(decoded.name()).isEqualTo("h3-29");
        assertThat(decoded.toString()).isEqualTo("h3-29");
        assertThat(decode("h4")).isNotEqualTo(ProtocolId.H2);
    }

    @Test
    public void testValueOfRejectsInvalidIds() {
        assertThatThrownBy(() -> ProtocolId.valueOf("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProtocolId.valueOf(new String(new char[256])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProtocolId.valueOf("hĀ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ProtocolId.valueOf(new String(new char[255])).name()).hasSize(255);
        assertThat(ProtocolId.valueOf("hÿ").name()).isEqualTo("hÿ");
    }

    private static ProtocolId decode(String name) {
        ByteBuf in = Unpooled.copiedBuffer("x" + name + "x", CharsetUtil.ISO_8859_1);
        try {
            return ProtocolId.valueOf(in, 1, name.length());
        } finally {
            in.release();
        }
    }
}
