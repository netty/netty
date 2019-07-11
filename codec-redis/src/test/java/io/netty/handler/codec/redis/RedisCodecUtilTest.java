/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.redis;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

public class RedisCodecUtilTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Rule public final Timeout globalTimeout = new Timeout(10000);

  /* testedClasses: RedisCodecUtil */
  // Test written by Diffblue Cover.
  @Test
  public void longToAsciiBytesInputPositiveOutput1() {

    // Arrange
    final long value = 5L;

    // Act
    final byte[] actual = RedisCodecUtil.longToAsciiBytes(value);

    // Assert result
    Assert.assertArrayEquals(new byte[] {(byte)53}, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void makeShortInputNotNullNotNullOutputZero() {

    // Arrange
    final char first = '\u0000';
    final char second = '\u0000';

    // Act
    final short actual = RedisCodecUtil.makeShort(first, second);

    // Assert result
    Assert.assertEquals((short)0, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void makeShortInputNotNullNotNullOutputZero2() {

    // Arrange
    final char first = '\u0000';
    final char second = '\u0000';

    // Act
    final short actual = RedisCodecUtil.makeShort(first, second);

    // Assert result
    Assert.assertEquals((short)0, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void shortToBytesInputPositiveOutput2() {

    // Arrange
    final short value = (short)2;

    // Act
    final byte[] actual = RedisCodecUtil.shortToBytes(value);

    // Assert result
    Assert.assertArrayEquals(new byte[] {(byte)0, (byte)2}, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void shortToBytesInputZeroOutput2() {

    // Arrange
    final short value = (short)0;

    // Act
    final byte[] actual = RedisCodecUtil.shortToBytes(value);

    // Assert result
    Assert.assertArrayEquals(new byte[] {(byte)0, (byte)0}, actual);
  }
}
