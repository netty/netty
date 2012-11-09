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
package io.netty.monitor;

/**
 * <p>
 * Represents a {@code Monitor}'s unique name. This name is composed of
 * <ol>
 * <li>a {@code group}, an arbitrary name for a set of logically related
 * {@code Monitors}, e.g. "network";</li>
 * <li>a {@code type}, an arbitrary string identifying a {@code Monitor}'s
 * {@code type}, commonly denoting the resource to be monitored, e.g.
 * "client-connection";</li>
 * <li>a {@code name}, an arbitrary string identifying what is actually
 * monitored, e.g. "bytes-per-second"; and</li>
 * <li>an {@code instance} (optional), an arbitrary string identifying the exact
 * resource instance to be monitored, in case we aren't dealing with a singleton
 * resource, e.g. "client-connection#67AF4".</li>
 * </ol>
 * </p>
 * <p>
 * <strong>DISCLAIMER</strong> This class is heavily based on <a
 * href="http://metrics.codahale.com/">Yammer's</a>
 * {@link com.yammer.metrics.core.MetricName MetricName}.
 * </p>
 */
public final class MonitorName {

    private final String group;
    private final String type;
    private final String name;
    private final String instance;

    /**
     * Create a new {@code MonitorName}, using the supplied
     * {@code monitoredClass}'s {@code package name} as its {@link #getGroup()
     * group}, the {@code monitoredClass}'s {@code simple name} as its
     * {@link #getType()} and the supplied {@code name} as its
     * {@link #getName() name}.
     * @param monitoredClass The class to be monitored, i.e. a class that
     *            represents a resource whose statistics we are interested in
     * @param name Our new {@code MonitorName}'s {@link #getName() name}
     * @throws NullPointerException If either {@code monitoredClass} or
     *             {@code name} is {@code null}
     */
    public MonitorName(final Class<?> monitoredClass, final String name) {
        this(monitoredClass.getPackage() != null ? monitoredClass.getPackage().getName() : "", monitoredClass
                .getSimpleName().replaceAll("\\$$", ""), name, null);
    }

    /**
     * Create a new {@code MonitorName}, using the supplied
     * {@code monitoredClass}'s {@code package name} as its {@link #getGroup()
     * group}, the {@code monitoredClass}'s {@code simple name} as its
     * {@link #getType()}, the supplied {@code name} as its {@link #getName()
     * name} and the supplied {@code instance} as its {@link #getInstance()
     * instance}.
     * @param monitoredClass The class to be monitored, i.e. a class that
     *            represents a resource whose statistics we are interested in
     * @param name Our new {@code MonitorName}'s {@link #getName() name}
     * @param instance Our new {@code MonitorName}'s {@link #getInstance()
     *            instance}
     * @throws NullPointerException If either {@code monitoredClass} or
     *             {@code name} is {@code null}
     */
    public MonitorName(final Class<?> monitoredClass, final String name, final String instance) {
        this(monitoredClass.getPackage().getName(), monitoredClass.getSimpleName(), name, instance);
    }

    /**
     * Create a new {@code MonitorName} out of the supplied {@code group},
     * {@code type} and {@code name}.
     * @param group Our new {@code MonitorName}'s {@link #getGroup() group}
     * @param type Our new {@code MonitorName}'s {@link #getType() type}
     * @param name Our new {@code MonitorName}'s {@link #getName() name}
     * @throws NullPointerException If one of {@code group}, {@code type} and
     *             {@code name} is {@code null}
     */
    public MonitorName(final String group, final String type, final String name) {
        this(group, type, name, null);
    }

    /**
     * Create a new {@code MonitorName} out of the supplied {@code group},
     * {@code type}, {@code name} and {@code instance}
     * @param group Our new {@code MonitorName}'s {@link #getGroup() group}
     * @param type Our new {@code MonitorName}'s {@link #getType() type}
     * @param name Our new {@code MonitorName}'s {@link #getName() name}
     * @param instance Our new {@code MonitorName}'s {@link #getInstance()
     *            instance}
     * @throws NullPointerException If one of {@code group}, {@code type} and
     *             {@code name} is {@code null}
     */
    public MonitorName(final String group, final String type, final String name, final String instance) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.group = group;
        this.type = type;
        this.name = name;
        this.instance = instance;
    }

    /**
     * Returns a copy of this {@code MonitorName} with its
     * {@link #getInstance() instance} field replaced by the supplied
     * {@code instance}. Serves to support a poor man's templating mechanism for
     * {@code MonitorNames}.
     * @param instance The instance to be used in the {@code MonitorName}
     *            returned by this method
     * @return A copy of this {@code MonitorName} with its
     *         {@link #getInstance() instance} field replaced by the supplied
     *         {@code instance}
     * @throws NullPointerException If {@code instance} is {@code null}
     */
    public MonitorName ofInstance(final String instance) {
        if (instance == null) {
            throw new NullPointerException("instance");
        }
        if (instance.equals(this.instance)) {
            return this;
        }
        return new MonitorName(group, type, name, instance);
    }

    /**
     * This {@code MonitorName}'s {@code group}, an arbitrary name for a set of
     * logically related {@code Monitors}, e.g. "network".
     * @return The group, an arbitrary name for a set of logically related
     *         {@code Monitors}
     */
    public String getGroup() {
        return group;
    }

    /**
     * This {@code MonitorName}'s {@code type}, an arbitrary string identifying
     * a {@code Monitor}'s {@code type}, commonly denoting the resource to be
     * monitored, e.g. "client-connection".
     * @return The type, an arbitrary string identifying a {@code Monitor}'s
     *         {@code type}, commonly denoting the resource to be monitored
     */
    public String getType() {
        return type;
    }

    /**
     * This {@code MonitorName}'s {@code name}, an arbitrary string identifying
     * what is actually monitored, e.g. "bytes-per-second".
     * @return The name, an arbitrary string identifying what is actually
     *         monitored
     */
    public String getName() {
        return name;
    }

    /**
     * This {@code MonitorName}'s {@code instance} (optional), an arbitrary
     * string identifying the exact resource instance to be monitored, in case
     * we aren't dealing with a singleton resource, e.g.
     * "client-connection#67AF4".
     * @return The instance (optional), an arbitrary string identifying the
     *         exact resource instance to be monitored, in case we aren't
     *         dealing with a singleton resource
     */
    public String getInstance() {
        return instance;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (group == null ? 0 : group.hashCode());
        result = prime * result + (instance == null ? 0 : instance.hashCode());
        result = prime * result + (name == null ? 0 : name.hashCode());
        result = prime * result + (type == null ? 0 : type.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MonitorName other = (MonitorName) obj;
        if (group == null) {
            if (other.group != null) {
                return false;
            }
        } else if (!group.equals(other.group)) {
            return false;
        }
        if (instance == null) {
            if (other.instance != null) {
                return false;
            }
        } else if (!instance.equals(other.instance)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return instance != null ? "Monitor(" + group + '/' + type + '/' + name + '/' + instance + ')' : "Monitor("
                + group + '/' + type + '/' + name + ')';
    }
}
