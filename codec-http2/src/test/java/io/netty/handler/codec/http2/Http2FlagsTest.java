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
package io.netty.handler.codec.http2;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

public class Http2FlagsTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Rule public final Timeout globalTimeout = new Timeout(10000);

  /* testedClasses: Http2Flags */
  // Test written by Diffblue Cover.
  @Test
  public void ackOutputFalse() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final boolean actual = objectUnderTest.ack();

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void ackOutputTrue() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)1);

    // Act
    final boolean actual = objectUnderTest.ack();

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void endOfHeadersOutputFalse() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final boolean actual = objectUnderTest.endOfHeaders();

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void endOfHeadersOutputTrue() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)4);

    // Act
    final boolean actual = objectUnderTest.endOfHeaders();

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void endOfStreamOutputFalse() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final boolean actual = objectUnderTest.endOfStream();

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void endOfStreamOutputTrue() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)1);

    // Act
    final boolean actual = objectUnderTest.endOfStream();

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void equalsInputNullOutputFalse() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();
    final Object obj = null;

    // Act
    final boolean actual = objectUnderTest.equals(obj);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void getNumPriorityBytesOutputPositive() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)32);

    // Act
    final int actual = objectUnderTest.getNumPriorityBytes();

    // Assert result
    Assert.assertEquals(5, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void getNumPriorityBytesOutputZero() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final int actual = objectUnderTest.getNumPriorityBytes();

    // Assert result
    Assert.assertEquals(0, actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void getPaddingPresenceFieldLengthOutputPositive() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)8);

    // Act
    final int actual = objectUnderTest.getPaddingPresenceFieldLength();

    // Assert result
    Assert.assertEquals(1, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void getPaddingPresenceFieldLengthOutputZero() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final int actual = objectUnderTest.getPaddingPresenceFieldLength();

    // Assert result
    Assert.assertEquals(0, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void hashCodeOutputPositive() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final int actual = objectUnderTest.hashCode();

    // Assert result
    Assert.assertEquals(31, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isFlagSetInputPositiveOutputFalse() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();
    final short mask = (short)2;

    // Act
    final boolean actual = objectUnderTest.isFlagSet(mask);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void isFlagSetInputPositiveOutputTrue() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)1);
    final short mask = (short)1;

    // Act
    final boolean actual = objectUnderTest.isFlagSet(mask);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void paddingPresentOutputFalse() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final boolean actual = objectUnderTest.paddingPresent();

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void paddingPresentOutputTrue() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)8);

    // Act
    final boolean actual = objectUnderTest.paddingPresent();

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void priorityPresentOutputFalse() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final boolean actual = objectUnderTest.priorityPresent();

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void priorityPresentOutputTrue() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)32);

    // Act
    final boolean actual = objectUnderTest.priorityPresent();

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void toStringOutputNotNull() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags();

    // Act
    final String actual = objectUnderTest.toString();

    // Assert result
    Assert.assertEquals("value = 0 ()", actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void toStringOutputNotNull2() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)-8245);

    // Act
    final String actual = objectUnderTest.toString();

    // Assert result
    Assert.assertEquals("value = -8245 (ACK,END_OF_STREAM,PADDING_PRESENT,)", actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void toStringOutputNotNull3() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)-8211);

    // Act
    final String actual = objectUnderTest.toString();

    // Assert result
    Assert.assertEquals(
        "value = -8211 (ACK,END_OF_HEADERS,END_OF_STREAM,PRIORITY_PRESENT,PADDING_PRESENT,)",
        actual);
  }

  // Test written by Diffblue Cover.

  @Test
  public void valueOutputZero() {

    // Arrange
    final Http2Flags objectUnderTest = new Http2Flags((short)0);

    // Act
    final short actual = objectUnderTest.value();

    // Assert result
    Assert.assertEquals((short)0, actual);
  }
}
