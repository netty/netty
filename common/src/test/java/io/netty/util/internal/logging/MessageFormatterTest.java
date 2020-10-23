/*
 * Copyright 2017 The Netty Project
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
/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 * <p>
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 * <p>
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 * <p>
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.netty.util.internal.logging;

import org.junit.Test;

import static org.junit.Assert.*;

public class MessageFormatterTest {

    @Test
    public void testNull() {
        String result = MessageFormatter.format(null, 1).getMessage();
        assertNull(result);
    }

    @Test
    public void nullParametersShouldBeHandledWithoutBarfing() {
        String result = MessageFormatter.format("Value is {}.", null).getMessage();
        assertEquals("Value is null.", result);

        result = MessageFormatter.format("Val1 is {}, val2 is {}.", null, null).getMessage();
        assertEquals("Val1 is null, val2 is null.", result);

        result = MessageFormatter.format("Val1 is {}, val2 is {}.", 1, null).getMessage();
        assertEquals("Val1 is 1, val2 is null.", result);

        result = MessageFormatter.format("Val1 is {}, val2 is {}.", null, 2).getMessage();
        assertEquals("Val1 is null, val2 is 2.", result);

        result = MessageFormatter.arrayFormat(
                "Val1 is {}, val2 is {}, val3 is {}", new Integer[] { null, null, null }).getMessage();
        assertEquals("Val1 is null, val2 is null, val3 is null", result);

        result = MessageFormatter.arrayFormat(
                "Val1 is {}, val2 is {}, val3 is {}", new Integer[] { null, 2, 3 }).getMessage();
        assertEquals("Val1 is null, val2 is 2, val3 is 3", result);

        result = MessageFormatter.arrayFormat(
                "Val1 is {}, val2 is {}, val3 is {}", new Integer[] { null, null, 3 }).getMessage();
        assertEquals("Val1 is null, val2 is null, val3 is 3", result);
    }

    @Test
    public void verifyOneParameterIsHandledCorrectly() {
        String result = MessageFormatter.format("Value is {}.", 3).getMessage();
        assertEquals("Value is 3.", result);

        result = MessageFormatter.format("Value is {", 3).getMessage();
        assertEquals("Value is {", result);

        result = MessageFormatter.format("{} is larger than 2.", 3).getMessage();
        assertEquals("3 is larger than 2.", result);

        result = MessageFormatter.format("No subst", 3).getMessage();
        assertEquals("No subst", result);

        result = MessageFormatter.format("Incorrect {subst", 3).getMessage();
        assertEquals("Incorrect {subst", result);

        result = MessageFormatter.format("Value is {bla} {}", 3).getMessage();
        assertEquals("Value is {bla} 3", result);

        result = MessageFormatter.format("Escaped \\{} subst", 3).getMessage();
        assertEquals("Escaped {} subst", result);

        result = MessageFormatter.format("{Escaped", 3).getMessage();
        assertEquals("{Escaped", result);

        result = MessageFormatter.format("\\{}Escaped", 3).getMessage();
        assertEquals("{}Escaped", result);

        result = MessageFormatter.format("File name is {{}}.", "App folder.zip").getMessage();
        assertEquals("File name is {App folder.zip}.", result);

        // escaping the escape character
        result = MessageFormatter.format("File name is C:\\\\{}.", "App folder.zip").getMessage();
        assertEquals("File name is C:\\App folder.zip.", result);
    }

    @Test
    public void testTwoParameters() {
        String result = MessageFormatter.format("Value {} is smaller than {}.", 1, 2).getMessage();
        assertEquals("Value 1 is smaller than 2.", result);

        result = MessageFormatter.format("Value {} is smaller than {}", 1, 2).getMessage();
        assertEquals("Value 1 is smaller than 2", result);

        result = MessageFormatter.format("{}{}", 1, 2).getMessage();
        assertEquals("12", result);

        result = MessageFormatter.format("Val1={}, Val2={", 1, 2).getMessage();
        assertEquals("Val1=1, Val2={", result);

        result = MessageFormatter.format("Value {} is smaller than \\{}", 1, 2).getMessage();
        assertEquals("Value 1 is smaller than {}", result);

        result = MessageFormatter.format("Value {} is smaller than \\{} tail", 1, 2).getMessage();
        assertEquals("Value 1 is smaller than {} tail", result);

        result = MessageFormatter.format("Value {} is smaller than \\{", 1, 2).getMessage();
        assertEquals("Value 1 is smaller than \\{", result);

        result = MessageFormatter.format("Value {} is smaller than {tail", 1, 2).getMessage();
        assertEquals("Value 1 is smaller than {tail", result);

        result = MessageFormatter.format("Value \\{} is smaller than {}", 1, 2).getMessage();
        assertEquals("Value {} is smaller than 1", result);
    }

    @Test
    public void testExceptionIn_toString() {
        Object o = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("a");
            }
        };
        String result = MessageFormatter.format("Troublesome object {}", o).getMessage();
        assertEquals("Troublesome object [FAILED toString()]", result);
    }

    @Test
    public void testNullArray() {
        String msg0 = "msg0";
        String msg1 = "msg1 {}";
        String msg2 = "msg2 {} {}";
        String msg3 = "msg3 {} {} {}";

        Object[] args = null;

        String result = MessageFormatter.arrayFormat(msg0, args).getMessage();
        assertEquals(msg0, result);

        result = MessageFormatter.arrayFormat(msg1, args).getMessage();
        assertEquals(msg1, result);

        result = MessageFormatter.arrayFormat(msg2, args).getMessage();
        assertEquals(msg2, result);

        result = MessageFormatter.arrayFormat(msg3, args).getMessage();
        assertEquals(msg3, result);
    }

    // tests the case when the parameters are supplied in a single array
    @Test
    public void testArrayFormat() {
        Integer[] ia0 = { 1, 2, 3 };

        String result = MessageFormatter.arrayFormat("Value {} is smaller than {} and {}.", ia0).getMessage();
        assertEquals("Value 1 is smaller than 2 and 3.", result);

        result = MessageFormatter.arrayFormat("{}{}{}", ia0).getMessage();
        assertEquals("123", result);

        result = MessageFormatter.arrayFormat("Value {} is smaller than {}.", ia0).getMessage();
        assertEquals("Value 1 is smaller than 2.", result);

        result = MessageFormatter.arrayFormat("Value {} is smaller than {}", ia0).getMessage();
        assertEquals("Value 1 is smaller than 2", result);

        result = MessageFormatter.arrayFormat("Val={}, {, Val={}", ia0).getMessage();
        assertEquals("Val=1, {, Val=2", result);

        result = MessageFormatter.arrayFormat("Val={}, {, Val={}", ia0).getMessage();
        assertEquals("Val=1, {, Val=2", result);

        result = MessageFormatter.arrayFormat("Val1={}, Val2={", ia0).getMessage();
        assertEquals("Val1=1, Val2={", result);
    }

    @Test
    public void testArrayValues() {
        Integer[] p1 = { 2, 3 };

        String result = MessageFormatter.format("{}{}", 1, p1).getMessage();
        assertEquals("1[2, 3]", result);

        // Integer[]
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", p1 }).getMessage();
        assertEquals("a[2, 3]", result);

        // byte[]
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", new byte[] { 1, 2 } }).getMessage();
        assertEquals("a[1, 2]", result);

        // int[]
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", new int[] { 1, 2 } }).getMessage();
        assertEquals("a[1, 2]", result);

        // float[]
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", new float[] { 1, 2 } }).getMessage();
        assertEquals("a[1.0, 2.0]", result);

        // double[]
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", new double[] { 1, 2 } }).getMessage();
        assertEquals("a[1.0, 2.0]", result);
    }

    @Test
    public void testMultiDimensionalArrayValues() {
        Integer[] ia0 = { 1, 2, 3 };
        Integer[] ia1 = { 10, 20, 30 };

        Integer[][] multiIntegerA = { ia0, ia1 };
        String result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", multiIntegerA }).getMessage();
        assertEquals("a[[1, 2, 3], [10, 20, 30]]", result);

        int[][] multiIntA = { { 1, 2 }, { 10, 20 } };
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", multiIntA }).getMessage();
        assertEquals("a[[1, 2], [10, 20]]", result);

        float[][] multiFloatA = { { 1, 2 }, { 10, 20 } };
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", multiFloatA }).getMessage();
        assertEquals("a[[1.0, 2.0], [10.0, 20.0]]", result);

        Object[][] multiOA = { ia0, ia1 };
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", multiOA }).getMessage();
        assertEquals("a[[1, 2, 3], [10, 20, 30]]", result);

        Object[][][] _3DOA = { multiOA, multiOA };
        result = MessageFormatter.arrayFormat("{}{}", new Object[] { "a", _3DOA }).getMessage();
        assertEquals("a[[[1, 2, 3], [10, 20, 30]], [[1, 2, 3], [10, 20, 30]]]", result);

        Byte[] ba0 = { 0, Byte.MAX_VALUE, Byte.MIN_VALUE };
        Short[] sa0 = { 0, Short.MIN_VALUE, Short.MAX_VALUE };
        result = MessageFormatter.arrayFormat("{}\\{}{}", new Object[] { new Object[] { ba0, sa0 }, ia1 }).getMessage();
        assertEquals("[[0, 127, -128], [0, -32768, 32767]]{}[10, 20, 30]", result);
    }

    @Test
    public void testCyclicArrays() {
        Object[] cyclicA = new Object[1];
        cyclicA[0] = cyclicA;
        assertEquals("[[...]]", MessageFormatter.arrayFormat("{}", cyclicA).getMessage());

        Object[] a = new Object[2];
        a[0] = 1;
        Object[] c = { 3, a };
        Object[] b = { 2, c };
        a[1] = b;
        assertEquals("1[2, [3, [1, [...]]]]",
                     MessageFormatter.arrayFormat("{}{}", a).getMessage());
    }

    @Test
    public void testArrayThrowable() {
        FormattingTuple ft;
        Throwable t = new Throwable();
        Object[] ia = { 1, 2, 3, t };

        ft = MessageFormatter.arrayFormat("Value {} is smaller than {} and {}.", ia);
        assertEquals("Value 1 is smaller than 2 and 3.", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("{}{}{}", ia);
        assertEquals("123", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("Value {} is smaller than {}.", ia);
        assertEquals("Value 1 is smaller than 2.", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("Value {} is smaller than {}", ia);
        assertEquals("Value 1 is smaller than 2", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("Val={}, {, Val={}", ia);
        assertEquals("Val=1, {, Val=2", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("Val={}, \\{, Val={}", ia);
        assertEquals("Val=1, \\{, Val=2", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("Val1={}, Val2={", ia);
        assertEquals("Val1=1, Val2={", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("Value {} is smaller than {} and {}.", ia);
        assertEquals("Value 1 is smaller than 2 and 3.", ft.getMessage());
        assertEquals(t, ft.getThrowable());

        ft = MessageFormatter.arrayFormat("{}{}{}{}", ia);
        assertEquals("123java.lang.Throwable", ft.getMessage());
        assertNull(ft.getThrowable());
    }
}
