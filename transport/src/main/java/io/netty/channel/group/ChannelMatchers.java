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
 * Helper class which provides often used {@link ChannelMatcher} implementations.
 */
public final class ChannelMatchers {

    private static final ChannelMatcher ALL_MATCHER = new ChannelMatcher() {
        @Override
        public boolean matches(Channel channel) {
            return true;
        }
    };

    private static final ChannelMatcher SERVER_CHANNEL_MATCHER = isInstanceOf(ServerChannel.class);
    private static final ChannelMatcher NON_SERVER_CHANNEL_MATCHER = isNotInstanceOf(ServerChannel.class);

    private ChannelMatchers() {
        // static methods only
    }

    /**
     * Returns a {@link ChannelMatcher} that matches all {@link Channel}s.
     */
    public static ChannelMatcher all() {
        return ALL_MATCHER;
    }

    /**
     * Returns a {@link ChannelMatcher} that matches all {@link Channel}s except the given.
     */
    public static ChannelMatcher isNot(Channel channel) {
        return invert(is(channel));
    }

    /**
     * Returns a {@link ChannelMatcher} that matches the given {@link Channel}.
     */
    public static ChannelMatcher is(Channel channel) {
        return new InstanceMatcher(channel);
    }

    /**
     * Returns a {@link ChannelMatcher} that matches all {@link Channel}s that are an instance of sub-type of
     * the given class.
     */
    public static ChannelMatcher isInstanceOf(Class<? extends Channel> clazz) {
        return new ClassMatcher(clazz);
    }

    /**
     * Returns a {@link ChannelMatcher} that matches all {@link Channel}s that are <strong>not</strong> an
     * instance of sub-type of the given class.
     */
    public static ChannelMatcher isNotInstanceOf(Class<? extends Channel> clazz) {
        return invert(isInstanceOf(clazz));
    }

    /**
     * Returns a {@link ChannelMatcher} that matches all {@link Channel}s that are of type {@link ServerChannel}.
     */
    public static ChannelMatcher isServerChannel() {
         return SERVER_CHANNEL_MATCHER;
    }

    /**
     * Returns a {@link ChannelMatcher} that matches all {@link Channel}s that are <strong>not</strong> of type
     * {@link ServerChannel}.
     */
    public static ChannelMatcher isNonServerChannel() {
        return NON_SERVER_CHANNEL_MATCHER;
    }

    /**
     * Invert the given {@link ChannelMatcher}.
     */
    public static ChannelMatcher invert(ChannelMatcher matcher) {
        return new InvertMatcher(matcher);
    }

    /**
     * Return a composite of the given {@link ChannelMatcher}s. This means all {@link ChannelMatcher} must
     * return {@code true} to match.
     */
    public static ChannelMatcher compose(ChannelMatcher... matchers) {
        if (matchers.length < 1) {
            throw new IllegalArgumentException("matchers must at least contain one element");
        }
        if (matchers.length == 1) {
            return matchers[0];
        }
        return new CompositeMatcher(matchers);
    }

    private static final class CompositeMatcher implements ChannelMatcher {
        private final ChannelMatcher[] matchers;

        CompositeMatcher(ChannelMatcher... matchers) {
            this.matchers = matchers;
        }

        @Override
        public boolean matches(Channel channel) {
            for (ChannelMatcher m: matchers) {
                if (!m.matches(channel)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class InvertMatcher implements ChannelMatcher {
        private final ChannelMatcher matcher;

        InvertMatcher(ChannelMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public boolean matches(Channel channel) {
            return !matcher.matches(channel);
        }
    }

    private static final class InstanceMatcher implements ChannelMatcher {
        private final Channel channel;

        InstanceMatcher(Channel channel) {
            this.channel = channel;
        }

        @Override
        public boolean matches(Channel ch) {
            return channel == ch;
        }
    }

    private static final class ClassMatcher implements ChannelMatcher {
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
