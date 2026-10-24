/*
 * Copyright 2020 The Netty Project
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
package io.netty.jfr;

import io.netty.util.ResourceLeakDetector;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * A {@link ResourceLeakDetector} that creates JDK Flight Recorder events for all resource leaks.
 */
public final class JfrInstrumentedResourceLeakDetector<T> extends ResourceLeakDetector<T> {

    public JfrInstrumentedResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
        super(resourceType, samplingInterval);
    }

    @Override
    protected void reportTracedLeak(String resourceType, String records) {
        LeakEvent event = new LeakEvent();
        event.setResourceType(resourceType);
        event.setRecords(records);
        event.commit();
    }

    @Override
    protected void reportUntracedLeak(String resourceType) {
        LeakEvent event = new LeakEvent();
        event.setResourceType(resourceType);
        event.commit();
    }

    @Category({ "Netty", "Leak Detection" })
    @Label("Leak")
    @Name("io.netty.leakdetection.Leak")
    @Enabled(true)
    @Description("LEAK: release() was not called on the resource type before it's garbage-collected. " +
                "See https://netty.io/wiki/reference-counted-objects.html for more information.")
    private static final class LeakEvent extends Event {
        @Label("Resource Type")
        private String resourceType;
        @Label("Records")
        @Description("Recent access records. If value is not set, see link in event type description on " +
                     "how to enable more advanced leak detection.")
        private String records;

        void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }
        void setRecords(String records) {
            this.records = records;
        }
    }
}
