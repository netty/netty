/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testcases for io.netty.util.internal.ObjectUtil.
 *
 * The tests for exceptions do not use a fail mimic. The tests evaluate the
 * presence and type, to have really regression character.
 *
 */
public class ObjectUtilTest {

    private static final Object NULL_OBJECT = null;

    private static final Object NON_NULL_OBJECT = "Object is not null";
    private static final String NON_NULL_EMPTY_STRING = "";
    private static final String NON_NULL_WHITESPACE_STRING = "  ";
    private static final Object[] NON_NULL_EMPTY_OBJECT_ARRAY = {};
    private static final Object[] NON_NULL_FILLED_OBJECT_ARRAY = { NON_NULL_OBJECT };
    private static final CharSequence NULL_CHARSEQUENCE = (CharSequence) NULL_OBJECT;
    private static final CharSequence NON_NULL_CHARSEQUENCE = (CharSequence) NON_NULL_OBJECT;
    private static final CharSequence NON_NULL_EMPTY_CHARSEQUENCE = (CharSequence) NON_NULL_EMPTY_STRING;
    private static final byte[] NON_NULL_EMPTY_BYTE_ARRAY = {};
    private static final byte[] NON_NULL_FILLED_BYTE_ARRAY = { (byte) 0xa };
    private static final char[] NON_NULL_EMPTY_CHAR_ARRAY = {};
    private static final char[] NON_NULL_FILLED_CHAR_ARRAY = { 'A' };

    private static final String NULL_NAME = "IS_NULL";
    private static final String NON_NULL_NAME = "NOT_NULL";
    private static final String NON_NULL_EMPTY_NAME = "NOT_NULL_BUT_EMPTY";

    private static final String TEST_RESULT_NULLEX_OK = "Expected a NPE/IAE";
    private static final String TEST_RESULT_NULLEX_NOK = "Expected no exception";
    private static final String TEST_RESULT_EXTYPE_NOK = "Expected type not found";

    private static final int ZERO_INT = 0;
    private static final long ZERO_LONG = 0;
    private static final double ZERO_DOUBLE = 0.0d;
    private static final float ZERO_FLOAT = 0.0f;

    private static final int POS_ONE_INT = 1;
    private static final long POS_ONE_LONG = 1;
    private static final double POS_ONE_DOUBLE = 1.0d;
    private static final float POS_ONE_FLOAT = 1.0f;

    private static final int NEG_ONE_INT = -1;
    private static final long NEG_ONE_LONG = -1;
    private static final double NEG_ONE_DOUBLE = -1.0d;
    private static final float NEG_ONE_FLOAT = -1.0f;

    private static final String NUM_POS_NAME = "NUMBER_POSITIVE";
    private static final String NUM_ZERO_NAME = "NUMBER_ZERO";
    private static final String NUM_NEG_NAME = "NUMBER_NEGATIVE";

    @Test
    public void testCheckNotNull() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkNotNull(NON_NULL_OBJECT, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNotNull(NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckNotNullWithIAE() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkNotNullWithIAE(NON_NULL_OBJECT, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNotNullWithIAE(NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckNotNullArrayParam() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkNotNullArrayParam(NON_NULL_OBJECT, 1, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNotNullArrayParam(NULL_OBJECT, 1, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveIntString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositive(POS_ONE_INT, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(ZERO_INT, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(NEG_ONE_INT, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveLongString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositive(POS_ONE_LONG, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(ZERO_LONG, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(NEG_ONE_LONG, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveDoubleString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositive(POS_ONE_DOUBLE, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(ZERO_DOUBLE, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(NEG_ONE_DOUBLE, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveFloatString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositive(POS_ONE_FLOAT, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(ZERO_FLOAT, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositive(NEG_ONE_FLOAT, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveOrZeroIntString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(POS_ONE_INT, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(ZERO_INT, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(NEG_ONE_INT, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveOrZeroLongString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(POS_ONE_LONG, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(ZERO_LONG, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(NEG_ONE_LONG, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveOrZeroDoubleString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(POS_ONE_DOUBLE, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(ZERO_DOUBLE, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(NEG_ONE_DOUBLE, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckPositiveOrZeroFloatString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(POS_ONE_FLOAT, NUM_POS_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(ZERO_FLOAT, NUM_ZERO_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkPositiveOrZero(NEG_ONE_FLOAT, NUM_NEG_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckNonEmptyTArrayString() {
        Exception actualEx = null;

        try {
            ObjectUtil.checkNonEmpty((Object[]) NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((Object[]) NON_NULL_FILLED_OBJECT_ARRAY, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((Object[]) NON_NULL_EMPTY_OBJECT_ARRAY, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckNonEmptyByteArrayString() {
        Exception actualEx = null;

        try {
            ObjectUtil.checkNonEmpty((byte[]) NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((byte[]) NON_NULL_FILLED_BYTE_ARRAY, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((byte[]) NON_NULL_EMPTY_BYTE_ARRAY, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckNonEmptyCharArrayString() {
        Exception actualEx = null;

        try {
            ObjectUtil.checkNonEmpty((char[]) NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((char[]) NON_NULL_FILLED_CHAR_ARRAY, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((char[]) NON_NULL_EMPTY_CHAR_ARRAY, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckNonEmptyTString() {
        Exception actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((Object[]) NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((Object[]) NON_NULL_FILLED_OBJECT_ARRAY, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((Object[]) NON_NULL_EMPTY_OBJECT_ARRAY, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }

    @Test
    public void testCheckNonEmptyStringString() {
        Exception actualEx = null;

        try {
            ObjectUtil.checkNonEmpty((String) NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((String) NON_NULL_OBJECT, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((String) NON_NULL_EMPTY_STRING, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((String) NON_NULL_WHITESPACE_STRING, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);
    }

    @Test
    public void testCheckNonEmptyCharSequenceString() {
        Exception actualEx = null;

        try {
            ObjectUtil.checkNonEmpty((CharSequence) NULL_CHARSEQUENCE, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((CharSequence) NON_NULL_CHARSEQUENCE, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((CharSequence) NON_NULL_EMPTY_CHARSEQUENCE, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmpty((CharSequence) NON_NULL_WHITESPACE_STRING, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);
    }

    @Test
    public void testCheckNonEmptyAfterTrim() {
        Exception actualEx = null;

        try {
            ObjectUtil.checkNonEmptyAfterTrim((String) NULL_OBJECT, NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof NullPointerException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmptyAfterTrim((String) NON_NULL_OBJECT, NON_NULL_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNull(actualEx, TEST_RESULT_NULLEX_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmptyAfterTrim(NON_NULL_EMPTY_STRING, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);

        actualEx = null;
        try {
            ObjectUtil.checkNonEmptyAfterTrim(NON_NULL_WHITESPACE_STRING, NON_NULL_EMPTY_NAME);
        } catch (Exception e) {
            actualEx = e;
        }
        assertNotNull(actualEx, TEST_RESULT_NULLEX_OK);
        assertTrue(actualEx instanceof IllegalArgumentException, TEST_RESULT_EXTYPE_NOK);
    }
}
