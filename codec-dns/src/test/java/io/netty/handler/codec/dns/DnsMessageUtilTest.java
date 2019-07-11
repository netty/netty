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
package io.netty.handler.codec.dns;

import io.netty.util.internal.StringUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.lang.reflect.Method;

public class DnsMessageUtilTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Rule public final Timeout globalTimeout = new Timeout(10000);

  /* testedClasses: DnsMessageUtil */
  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNotNullPositiveOutputNotNull() {

    // Arrange
    final StringBuilder buf = new StringBuilder("?");
    final int dnsClass = 7;

    // Act
    final StringBuilder actual = DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Assert side effects
    Assert.assertNotNull(buf);
    Assert.assertEquals("?UNKNOWN(7)", buf.toString());

    // Assert result
    Assert.assertNotNull(actual);
    Assert.assertEquals("?UNKNOWN(7)", actual.toString());
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNotNullPositiveOutputNotNull2() {

    // Arrange
    final StringBuilder buf = new StringBuilder("?");
    final int dnsClass = 254;

    // Act
    final StringBuilder actual = DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Assert side effects
    Assert.assertNotNull(buf);
    Assert.assertEquals("?NONE", buf.toString());

    // Assert result
    Assert.assertNotNull(actual);
    Assert.assertEquals("?NONE", actual.toString());
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNotNullPositiveOutputNotNull3() {

    // Arrange
    final StringBuilder buf = new StringBuilder("???");
    final int dnsClass = 1;

    // Act
    final StringBuilder actual = DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Assert side effects
    Assert.assertNotNull(buf);
    Assert.assertEquals("???IN", buf.toString());

    // Assert result
    Assert.assertNotNull(actual);
    Assert.assertEquals("???IN", actual.toString());
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNotNullPositiveOutputNotNull4() {

    // Arrange
    final StringBuilder buf = new StringBuilder("???");
    final int dnsClass = 4;

    // Act
    final StringBuilder actual = DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Assert side effects
    Assert.assertNotNull(buf);
    Assert.assertEquals("???HESIOD", buf.toString());

    // Assert result
    Assert.assertNotNull(actual);
    Assert.assertEquals("???HESIOD", actual.toString());
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNotNullPositiveOutputNotNull5() {

    // Arrange
    final StringBuilder buf = new StringBuilder("???");
    final int dnsClass = 2;

    // Act
    final StringBuilder actual = DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Assert side effects
    Assert.assertNotNull(buf);
    Assert.assertEquals("???CSNET", buf.toString());

    // Assert result
    Assert.assertNotNull(actual);
    Assert.assertEquals("???CSNET", actual.toString());
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNullPositiveOutputNullPointerException() {

    // Arrange
    final StringBuilder buf = null;
    final int dnsClass = 3;

    // Act
    thrown.expect(NullPointerException.class);
    DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Method is not expected to return due to exception thrown
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNullPositiveOutputNullPointerException2() {

    // Arrange
    final StringBuilder buf = null;
    final int dnsClass = 7;

    // Act
    thrown.expect(NullPointerException.class);
    DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Method is not expected to return due to exception thrown
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNullPositiveOutputNullPointerException3() {

    // Arrange
    final StringBuilder buf = null;
    final int dnsClass = 255;

    // Act
    thrown.expect(NullPointerException.class);
    DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Method is not expected to return due to exception thrown
  }

  // Test written by Diffblue Cover.
  @Test
  public void appendRecordClassInputNullPositiveOutputNullPointerException4() {

    // Arrange
    final StringBuilder buf = null;
    final int dnsClass = 254;

    // Act
    thrown.expect(NullPointerException.class);
    DnsMessageUtil.appendRecordClass(buf, dnsClass);

    // Method is not expected to return due to exception thrown
  }
}
