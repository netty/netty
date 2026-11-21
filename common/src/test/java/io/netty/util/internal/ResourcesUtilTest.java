/*
 * Copyright 2026 The Netty Project
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
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourcesUtilTest {

    @Test
    public void getFileDecodesUrl(@TempDir Path dir) throws Exception {
        Path path = Files.createFile(dir.resolve("a file with spaces.txt"));
        URL url = path.toUri().toURL();
        assertThat(url.getFile()).contains("%20");

        File file = ResourcesUtil.getFile(url);
        assertThat(file).isEqualTo(path.toFile());
        assertThat(file).exists();
    }

    @Test
    public void getFileOfMissingResourceThrows() {
        URL missing = ResourcesUtilTest.class.getResource("does-not-exist");
        assertThatThrownBy(() -> ResourcesUtil.getFile(missing))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resource not found");
    }
}
