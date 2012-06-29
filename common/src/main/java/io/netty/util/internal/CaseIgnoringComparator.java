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
package io.netty.util.internal;

import java.io.Serializable;
import java.util.Comparator;

/**
 * An implementation of {@link Comparator} for {@link String}s while ignoring
 * upper/lower case differences.
 */
public final class CaseIgnoringComparator implements Comparator<String>, Serializable {

    /**
     * The serial version unique ID
     */
    private static final long serialVersionUID = 4582133183775373862L;

    /**
     * The running {@link CaseIgnoringComparator}
     */
    public static final CaseIgnoringComparator INSTANCE = new CaseIgnoringComparator();

    /**
     * A dummy constructor to stop external construction
     */
    private CaseIgnoringComparator() {
    }

    /**
     * Compares two {@link String}s
     * 
     * @param original The original {@link String} that is being used
     * @param otherString The other {@link String} to compare with
     * @return 1 if the original string is greater, 0 if it is the same, or -1
     * if it is less than the other string
     */
    @Override
    public int compare(String original, String otherString) {
        return original.compareToIgnoreCase(otherString);
    }

    @SuppressWarnings("static-method")
    private Object readResolve() {
        return INSTANCE;
    }
}
