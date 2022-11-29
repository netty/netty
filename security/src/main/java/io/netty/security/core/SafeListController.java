/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core;

import io.netty.util.internal.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p> This class provides safe mechanism for adding or remove for class
 * extending {@link SafeListController}. </p>
 *
 * <p>
 * To add elements into this, you must unlock this class first.
 * Call {@link #unlock()} and then call {@link #add(Object)}.
 * Once you're done adding elements, lock this class using {@link #lock()}.
 * This will trigger update process and elements will be sorted and filtered
 * if necessary.
 * </p>
 *
 * @param <T> Element type
 */
public abstract class SafeListController<T> implements LockMechanism {

    // Main List should be accessible from Parent class
    // because Parent class will use it for operations.
    protected final List<T> MAIN_LIST = new ArrayList<>();

    // Copy List should be private because it is internally
    // used for copying elements from Main List and vice-versa.
    private final List<T> COPY_LIST = new ArrayList<>();

    // SortAndFilter can be null if there is no need
    // of sorting and filtering.
    private final SortAndFilter<T> sortAndFilter;

    /**
     * When lock is set to true then directly modifying the {@link List}
     * will result in an exception.
     */
    private boolean isLocked = true;

    protected SafeListController() {
        this(null);
    }

    protected SafeListController(SortAndFilter<T> sortAndFilter) {
        this.sortAndFilter = sortAndFilter;
    }

    /**
     * Lock {@link SafeListController} so no more modification can take place
     *
     * @throws IllegalStateException If Instance is already locked
     */
    @Override
    public synchronized void lock() {
        if (isLocked) {
            throw new IllegalStateException("Instance is already locked");
        }
        isLocked = true;

        // Reload
        List<T> tempHolderList;

        // Sort and Filter if required.
        if (sortAndFilter != null) {
            tempHolderList = sortAndFilter.process(COPY_LIST);
        } else {
            tempHolderList = COPY_LIST;
        }

        // Clear Main List before adding all elements of
        // Copy List.
        MAIN_LIST.clear();
        MAIN_LIST.addAll(tempHolderList);

        // Clear both temporary Lists
        COPY_LIST.clear();
        tempHolderList.clear();
    }

    /**
     * Unlock {@link SafeListController} for modification
     *
     * @throws IllegalStateException If Instance is already unlocked
     */
    @Override
    public synchronized void unlock() {
        if (!isLocked) {
            throw new IllegalStateException("Instance is already unlocked");
        }
        isLocked = false;

        // Add all items from Main List to Copy List
        COPY_LIST.addAll(MAIN_LIST);
    }

    /**
     * Unlock and lock for modification and finalization
     *
     * @throws IllegalStateException If Instance is already unlocked
     */
    public synchronized void unlockAndLock() {
        if (!isLocked) {
            throw new IllegalStateException("Instance is already unlocked");
        }

        // Unlock for modification
        unlock();

        // Lock to sort and finalize
        lock();
    }

    /**
     * Add an element
     *
     * @param element Element to add
     * @return true
     * @throws IllegalStateException Cannot add element when instance is locked
     */
    public synchronized boolean add(T element) {
        if (isLocked) {
            throw new IllegalStateException("Cannot add element when instance is locked." +
                    " Call #unlock before adding element.");
        }
        ObjectUtil.checkNotNull(element, element.getClass().getSimpleName());
        return COPY_LIST.add(element);
    }

    /**
     * Remove an element
     *
     * @param element Element to remove
     * @return true if remove was successful else false
     * @throws IllegalStateException Cannot remove element when instance is locked
     */
    public synchronized boolean remove(T element) {
        if (isLocked) {
            throw new IllegalStateException("Cannot remove element when instance is locked." +
                    " Call #unlock before removing element.");
        }
        ObjectUtil.checkNotNull(element, element.getClass().getSimpleName());
        return COPY_LIST.remove(element);
    }

    /**
     * Return an unmodifiable {@link List} of Main List.
     */
    public List<T> copy() {
        return Collections.unmodifiableList(MAIN_LIST);
    }

    /**
     * Returns {@code true} if this class is locked else {@code false}
     */
    @Override
    public boolean isLocked() {
        return isLocked;
    }

    /**
     * Functions that need to sort elements in {@link List} should
     * implement this interface.
     *
     * @param <T> Element type
     */
    public interface SortAndFilter<T> {

        /**
         * Sort and Filter
         *
         * @param list {@link List} containing elements to be sorted and filter.
         * @return {@link List} containing element that are sorted and filtered.
         */
        List<T> process(List<T> list);
    }
}
