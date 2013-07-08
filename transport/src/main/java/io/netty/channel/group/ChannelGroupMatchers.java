/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.group;

import io.netty.channel.Channel;
import io.netty.channel.ServerChannel;

/**
 * Helper class which provides often used {@link ChannelGroupMatcher} implementations.
 */
public final class ChannelGroupMatchers {

    private static final ChannelGroupMatcher ALL_MATCHER = new ChannelGroupMatcher() {
        @Override
        public boolean matches(Channel channel) {
            return true;
        }
    };

    private static final ChannelGroupMatcher SERVER_CHANNEL_MATCHER = isInstanceOf(ServerChannel.class);
    private static final ChannelGroupMatcher NON_SERVER_CHANNEL_MATCHER = isNotInstanceOf(ServerChannel.class);

    private ChannelGroupMatchers() {
        // static methods only
    }

    /**
     * Returns a {@link ChannelGroupMatcher} that matches all {@link Channel}s.
     */
    public static ChannelGroupMatcher all() {
        return ALL_MATCHER;
    }

    /**
     * Returns a {@link ChannelGroupMatcher} that matches all {@link Channel}s except the given.
     */
    public static ChannelGroupMatcher isNot(Channel channel) {
        return invert(is(channel));
    }

    /**
     * Returns a {@link ChannelGroupMatcher} that matches the given {@link Channel}.
     */
    public static ChannelGroupMatcher is(Channel channel) {
        return new InstanceMatcher(channel);
    }

    /**
     * Returns a {@link ChannelGroupMatcher} that matches all {@link Channel}s that are an instance of sub-type of
     * the given class.
     */
    public static ChannelGroupMatcher isInstanceOf(Class<? extends Channel> clazz) {
        return new ClassMatcher(clazz);
    }

    /**
     * Returns a {@link ChannelGroupMatcher} that matches all {@link Channel}s that are <strong>not</strong> an
     * instance of sub-type of the given class.
     */
    public static ChannelGroupMatcher isNotInstanceOf(Class<? extends Channel> clazz) {
        return invert(isInstanceOf(clazz));
    }

    /**
     * Returns a {@link ChannelGroupMatcher} that matches all {@link Channel}s that are of type {@link ServerChannel}.
     */
    public static ChannelGroupMatcher isServerChannel() {
         return SERVER_CHANNEL_MATCHER;
    }

    /**
     * Returns a {@link ChannelGroupMatcher} that matches all {@link Channel}s that are <strong>not</strong> of type
     * {@link ServerChannel}.
     */
    public static ChannelGroupMatcher isNonServerChannel() {
        return NON_SERVER_CHANNEL_MATCHER;
    }

    /**
     * Invert the given {@link ChannelGroupMatcher}.
     */
    public static ChannelGroupMatcher invert(ChannelGroupMatcher matcher) {
        return new InvertMatcher(matcher);
    }

    /**
     * Return a composite of the given {@link ChannelGroupMatcher}s. This means all {@link ChannelGroupMatcher} must
     * return {@code true} to match.
     */
    public static ChannelGroupMatcher compose(ChannelGroupMatcher... matchers) {
        if (matchers.length < 1) {
            throw new IllegalArgumentException("matchers must at least contain one element");
        }
        if (matchers.length == 1) {
            return matchers[0];
        }
        return new CompositeMatcher(matchers);
    }

    private static final class CompositeMatcher implements  ChannelGroupMatcher {
        private final ChannelGroupMatcher[] matchers;

        CompositeMatcher(ChannelGroupMatcher... matchers) {
            this.matchers = matchers;
        }

        @Override
        public boolean matches(Channel channel) {
            for (int i = 0; i < matchers.length; i++) {
                if (!matchers[i].matches(channel)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class InvertMatcher implements ChannelGroupMatcher {
        private final ChannelGroupMatcher matcher;

        InvertMatcher(ChannelGroupMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public boolean matches(Channel channel) {
            return !matcher.matches(channel);
        }
    }

    private static final class InstanceMatcher implements ChannelGroupMatcher {
        private final Channel channel;

        InstanceMatcher(Channel channel) {
            this.channel = channel;
        }

        @Override
        public boolean matches(Channel ch) {
            return channel == ch;
        }
    }

    private static final class ClassMatcher implements ChannelGroupMatcher {
        private final Class<? extends Channel> clazz;

        ClassMatcher(Class<? extends Channel> clazz) {
            this.clazz = clazz;
        }

        @Override
        public boolean matches(Channel ch) {
            return clazz.isInstance(ch);
        }
    }
}
