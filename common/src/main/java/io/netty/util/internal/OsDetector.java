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

package io.netty.util.internal;

import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import kr.motd.maven.os.Detector;


/**
 * Use the same OS name and architecture detection and normalization logic as netty/tcnative
 */
public class OsDetector extends Detector {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OsDetector.class);

    private final String osName;
    private final String osArch;
    private final String osClassifier;

    public OsDetector() {
        super.detect(System.getProperties());
        osName = System.getProperty("os.detected.name");
        osArch = System.getProperty("os.detected.arch");
        osClassifier = System.getProperty("os.detected.classifier");
    }

    public String getOsName() {
        return osName;
    }

    public String getOsArch() {
        return osArch;
    }

    public String getOsClassifier() {
        return osClassifier;
    }

    @Override
    protected void log(String s) {
        logger.log(InternalLogLevel.INFO, s);
    }

    @Override
    protected void logProperty(String s, String s1) {
        logger.log(InternalLogLevel.INFO, String.format("%s: %s", s, s1));
    }
}
