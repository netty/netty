/*
 * Copyright 2012 The Netty Project
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
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.caliper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

final class StandardVm extends Vm {

    @Override public List<String> getVmSpecificOptions(MeasurementType type, Arguments arguments) {
        if (!arguments.getCaptureVmLog()) {
            return ImmutableList.of();
        }

        List<String> result = Lists.newArrayList(
                "-server", "-dsa", "-da", "-ea:io.netty...",
                "-Xms768m", "-Xmx768m", "-XX:MaxDirectMemorySize=768m",
                "-XX:+AggressiveOpts", "-XX:+UseBiasedLocking", "-XX:+UseFastAccessorMethods",
                "-XX:+UseStringCache", "-XX:+OptimizeStringConcat",
                "-XX:+HeapDumpOnOutOfMemoryError", "-Dio.netty.noResourceLeakDetection");

        if (type == MeasurementType.TIME) {
            Collections.addAll(
                    result,
                    "-XX:+PrintCompilation");
        } else {
            Collections.addAll(
                    result,
                    "-verbose:gc",
                    "-Xbatch",
                    "-XX:+UseSerialGC",
                    "-XX:+TieredCompilation");
        }

        return result;
    }

    public static String defaultVmName() {
        return "java";
    }
}
