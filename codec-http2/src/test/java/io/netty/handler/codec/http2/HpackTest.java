/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.InputStream;

public class HpackTest {

    private static final String TEST_DIR = '/' + HpackTest.class.getPackage().getName().replaceAll("\\.", "/")
            + "/testdata/";

    public static File[] files() {
        File[] files = ResourcesUtil.getFile(HpackTest.class, TEST_DIR).listFiles();
        ObjectUtil.checkNotNull(files, "files");
        return files;
    }

    @ParameterizedTest(name = "file = {0}")
    @MethodSource("files")
    public void test(File file) throws Exception {
        InputStream is = HpackTest.class.getResourceAsStream(TEST_DIR + file.getName());
        HpackTestCase hpackTestCase = HpackTestCase.load(is);
        hpackTestCase.testCompress();
        hpackTestCase.testDecompress();
    }
}
