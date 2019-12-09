/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.ResourcesUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

@RunWith(Parameterized.class)
public class HpackTest {

    private static final String TEST_DIR = '/' + HpackTest.class.getPackage().getName().replaceAll("\\.", "/")
            + "/testdata/";

    private final String fileName;

    public HpackTest(String fileName) {
        this.fileName = fileName;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        File[] files = ResourcesUtil.getFile(HpackTest.class, TEST_DIR).listFiles();
        ObjectUtil.checkNotNull(files, "files");

        ArrayList<Object[]> data = new ArrayList<Object[]>();
        for (File file : files) {
            data.add(new Object[]{file.getName()});
        }
        return data;
    }

    @Test
    public void test() throws Exception {
        InputStream is = HpackTest.class.getResourceAsStream(TEST_DIR + fileName);
        HpackTestCase hpackTestCase = HpackTestCase.load(is);
        hpackTestCase.testCompress();
        hpackTestCase.testDecompress();
    }
}
