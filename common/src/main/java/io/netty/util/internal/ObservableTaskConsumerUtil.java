/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.concurrent.ObservableTaskConsumer;

public final class ObservableTaskConsumerUtil {

    private ObservableTaskConsumerUtil() {
    }

    public static long completedTasks(Object obj) {
        if (!(obj instanceof ObservableTaskConsumer)) {
            return ObservableTaskConsumer.UNSUPPORTED;
        }
        final ObservableTaskConsumer consumer = (ObservableTaskConsumer) obj;
        return consumer.completedTasks();
    }

    public static long submittedTasks(Object obj) {
        if (!(obj instanceof ObservableTaskConsumer)) {
            return ObservableTaskConsumer.UNSUPPORTED;
        }
        final ObservableTaskConsumer consumer = (ObservableTaskConsumer) obj;
        return consumer.submittedTasks();
    }
}
