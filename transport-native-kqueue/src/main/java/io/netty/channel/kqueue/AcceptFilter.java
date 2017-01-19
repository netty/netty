/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

@UnstableApi
public final class AcceptFilter {
    static final AcceptFilter PLATFORM_UNSUPPORTED = new AcceptFilter("", "");
    private final String filterName;
    private final String filterArgs;

    public AcceptFilter(String filterName, String filterArgs) {
        this.filterName = ObjectUtil.checkNotNull(filterName, "filterName");
        this.filterArgs = ObjectUtil.checkNotNull(filterArgs, "filterArgs");
    }

    public String filterName() {
        return filterName;
    }

    public String filterArgs() {
        return filterArgs;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AcceptFilter)) {
            return false;
        }
        AcceptFilter rhs = (AcceptFilter) o;
        return filterName.equals(rhs.filterName) && filterArgs.equals(rhs.filterArgs);
    }

    @Override
    public int hashCode() {
        return 31 * (31 + filterName.hashCode()) + filterArgs.hashCode();
    }

    @Override
    public String toString() {
        return filterName + ", " + filterArgs;
    }
}
