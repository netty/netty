/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer;

import java.util.NoSuchElementException;

import static io.netty.buffer.RedBlackTree.Color.BLACK;
import static io.netty.buffer.RedBlackTree.Color.RED;

class RedBlackTree<Key extends Comparable<Key>> {

    enum Color {
        RED,
        BLACK
    }

    private Node<Key> root;

    private static final class Node<Key> {
        private Key key;
        private Node<Key> left, right;
        private Color color;
        private int size;

        private Node(Key key, Color color, int size) {
            this.key = key;
            this.color = color;
            this.size = size;
        }
    }

    // is node x red; false if x is null ?
    private static <Key> boolean isRed(Node<Key> x) {
        return x != null && x.color == RED;
    }

    // number of node in subtree rooted at x; 0 if x is null
    private static <Key> int size(Node<Key> x) {
        return x == null ? 0 : x.size;
    }

    /**
     * Returns the number of keys in this tree.
     *
     * @return the number of keys in this tree
     */
    public int size() {
        return size(root);
    }

    /**
     * Is this tree empty?
     *
     * @return {@code true} if this tree is empty and {@code false} otherwise
     */
    public boolean isEmpty() {
        return root == null;
    }

    private Node<Key> get(Key key) {
        return get(root, key);
    }

    /**
     * Find a Node hold the given key rooted at x
     *
     * @param key the key
     *
     * @return the Node if the key is in the tree and {@code null} if the key is not in
     * the tree
     *
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    private Node<Key> get(Node<Key> x, Key key) {
        if (key == null) {
            throw new IllegalArgumentException("argument to get() is null");
        }
        while (x != null) {
            int cmp = key.compareTo(x.key);
            if (cmp < 0) {
                x = x.left;
            } else if (cmp > 0) {
                x = x.right;
            } else {
                return x;
            }
        }
        return null;
    }

    /**
     * Does this tree contain the given key?
     *
     * @param key the key
     *
     * @return {@code true} if this tree contains {@code key} and {@code false} otherwise
     *
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    public boolean contains(Key key) {
        return get(key) != null;
    }

    /**
     * Inserts the specified key into the tree, throw an exception if the tree already
     * contains the specified key.
     *
     * @param key the key
     *
     * @throws IllegalArgumentException if {@code key} is {@code null} or {@code key} already existed
     */
    public void put(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("first argument to put() is null");
        }

        root = put(root, key);
        root.color = BLACK;
    }

    // insert the key in the subtree rooted at h
    private Node<Key> put(Node<Key> h, Key key) {
        if (h == null) {
            return new Node<Key>(key, RED, 1);
        }

        int cmp = key.compareTo(h.key);
        if (cmp < 0) {
            h.left = put(h.left, key);
        } else if (cmp > 0) {
            h.right = put(h.right, key);
        } else {
            throw new IllegalArgumentException("same key is not allowed");
        }

        // fix-up any right-leaning links
        if (isRed(h.right) && !isRed(h.left)) {
            h = rotateLeft(h);
        }
        return fixup(h);
    }

    /**
     * Removes the smallest key from the tree.
     *
     * @throws NoSuchElementException if the tree is empty
     */
    public void deleteMin() {
        if (isEmpty()) {
            throw new NoSuchElementException("calls deleteMin() with empty tree");
        }

        // if both children of root are black, set root to red
        if (!isRed(root.left) && !isRed(root.right)) {
            root.color = RED;
        }

        root = deleteMin(root);
        if (!isEmpty()) {
            root.color = BLACK;
        }
    }

    // delete the key with the minimum key rooted at h
    private Node<Key> deleteMin(Node<Key> h) {
        if (h.left == null) {
            return null;
        }

        if (!isRed(h.left) && !isRed(h.left.left)) {
            h = moveRedLeft(h);
        }

        h.left = deleteMin(h.left);
        return balance(h);
    }

    /**
     * Removes the largest key from the tree.
     *
     * @throws NoSuchElementException if the tree is empty
     */
    public void deleteMax() {
        if (isEmpty()) {
            throw new NoSuchElementException("calls deleteMax() with empty tree");
        }

        // if both children of root are black, set root to red
        if (!isRed(root.left) && !isRed(root.right)) {
            root.color = RED;
        }

        root = deleteMax(root);
        if (!isEmpty()) {
            root.color = BLACK;
        }
    }

    // delete the key with the maximum key rooted at h
    private Node<Key> deleteMax(Node<Key> h) {
        if (isRed(h.left)) {
            h = rotateRight(h);
        }

        if (h.right == null) {
            return null;
        }

        if (!isRed(h.right) && !isRed(h.right.left)) {
            h = moveRedRight(h);
        }

        h.right = deleteMax(h.right);

        return balance(h);
    }

    /**
     * Removes the specified key from this tree (if the key is in this tree).
     *
     * @param key the key
     *
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    public void delete(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("argument to delete() is null");
        }
        if (!contains(key)) {
            return;
        }

        // if both children of root are black, set root to red
        if (!isRed(root.left) && !isRed(root.right)) {
            root.color = RED;
        }

        root = delete(root, key);
        if (!isEmpty()) {
            root.color = BLACK;
        }
    }

    // delete the key with the given key rooted at h
    private Node<Key> delete(Node<Key> h, Key key) {
        assert get(h, key) != null;

        if (key.compareTo(h.key) < 0) {
            if (!isRed(h.left) && !isRed(h.left.left)) {
                h = moveRedLeft(h);
            }
            h.left = delete(h.left, key);
        } else {
            if (isRed(h.left)) {
                h = rotateRight(h);
            }
            if (key.compareTo(h.key) == 0 && h.right == null) {
                return null;
            }
            if (!isRed(h.right) && !isRed(h.right.left)) {
                h = moveRedRight(h);
            }
            if (key.compareTo(h.key) == 0) {
                Node<Key> x = min(h.right);
                h.key = x.key;
                h.right = deleteMin(h.right);
            } else {
                h.right = delete(h.right, key);
            }
        }
        return balance(h);
    }

    // make a left-leaning link lean to the right
    private Node<Key> rotateRight(Node<Key> h) {
        assert h != null && isRed(h.left);
        Node<Key> x = h.left;
        h.left = x.right;
        x.right = h;
        x.color = x.right.color;
        x.right.color = RED;
        x.size = h.size;
        h.size = size(h.left) + size(h.right) + 1;
        return x;
    }

    // make a right-leaning link lean to the left
    private Node<Key> rotateLeft(Node<Key> h) {
        assert h != null && isRed(h.right);
        Node<Key> x = h.right;
        h.right = x.left;
        x.left = h;
        x.color = x.left.color;
        x.left.color = RED;
        x.size = h.size;
        h.size = size(h.left) + size(h.right) + 1;
        return x;
    }

    // flip the colors of a node and its two children
    private void flipColors(Node<Key> h) {
        // h must have opposite color of its two children
        assert h != null && h.left != null && h.right != null;
        assert !isRed(h) && isRed(h.left) && isRed(h.right)
               || isRed(h) && !isRed(h.left) && !isRed(h.right);
        h.color = reverseColor(h.color);
        h.left.color = reverseColor(h.left.color);
        h.right.color = reverseColor(h.right.color);
    }

    private static Color reverseColor(Color color) {
        return color == RED ? BLACK : RED;
    }

    // Assuming that h is red and both h.left and h.left.left
    // are black, make h.left or one of its children red.
    private Node<Key> moveRedLeft(Node<Key> h) {
        assert h != null;
        assert isRed(h) && !isRed(h.left) && !isRed(h.left.left);

        flipColors(h);
        if (isRed(h.right.left)) {
            h.right = rotateRight(h.right);
            h = rotateLeft(h);
            flipColors(h);
        }
        return h;
    }

    // Assuming that h is red and both h.right and h.right.left
    // are black, make h.right or one of its children red.
    private Node<Key> moveRedRight(Node<Key> h) {
        assert h != null;
        assert isRed(h) && !isRed(h.right) && !isRed(h.right.left);
        flipColors(h);
        if (isRed(h.left.left)) {
            h = rotateRight(h);
            flipColors(h);
        }
        return h;
    }

    // restore red-black tree invariant
    private Node<Key> balance(Node<Key> h) {
        assert h != null;

        if (isRed(h.right)) {
            h = rotateLeft(h);
        }
        return fixup(h);
    }

    private Node<Key> fixup(Node<Key> h) {
        if (isRed(h.left) && isRed(h.left.left)) {
            h = rotateRight(h);
        }
        if (isRed(h.left) && isRed(h.right)) {
            flipColors(h);
        }

        h.size = size(h.left) + size(h.right) + 1;
        return h;
    }

    /**
     * Returns the smallest key in the tree.
     *
     * @return the smallest key in the tree
     *
     * @throws NoSuchElementException if the tree is empty
     */
    public Key min() {
        if (isEmpty()) {
            throw new NoSuchElementException("calls min() with empty tree");
        }
        return min(root).key;
    }

    // the smallest key in subtree rooted at x; null if no such key
    private Node<Key> min(Node<Key> x) {
        while (true) {
            assert x != null;
            if (x.left == null) {
                return x;
            } else {
                x = x.left;
            }
        }
    }

    /**
     * Returns the largest key in the tree.
     *
     * @return the largest key in the tree
     *
     * @throws NoSuchElementException if the tree is empty
     */
    public Key max() {
        if (isEmpty()) {
            throw new NoSuchElementException("calls max() with empty tree");
        }
        return max(root).key;
    }

    // the largest key in the subtree rooted at x; null if no such key
    private Node<Key> max(Node<Key> x) {
        while (true) {
            assert x != null;
            if (x.right == null) {
                return x;
            } else {
                x = x.right;
            }
        }
    }

    /**
     * Returns the largest key in the tree less than or equal to {@code key}.
     *
     * @param key the key
     *
     * @return the largest key in the tree less than or equal to {@code key}
     *
     * @throws NoSuchElementException if there is no such key
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    public Key floor(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("argument to floor() is null");
        }
        if (isEmpty()) {
            throw new NoSuchElementException("calls floor() with empty tree");
        }
        Node<Key> x = floor(root, key);
        if (x == null) {
            throw new NoSuchElementException("argument to floor() is too small");
        } else {
            return x.key;
        }
    }

    // the largest key in the subtree rooted at x less than or equal to the given key
    private Node<Key> floor(Node<Key> x, Key key) {
        while (true) {
            if (x == null) {
                return null;
            }
            int cmp = key.compareTo(x.key);
            if (cmp == 0) {
                return x;
            }
            if (cmp < 0) {
                x = x.left;
                continue;
            }
            Node<Key> t = floor(x.right, key);
            if (t != null) {
                return t;
            } else {
                return x;
            }
        }
    }

    /**
     * Returns the smallest key in the tree greater than or equal to {@code key}.
     *
     * @param key the key
     *
     * @return the smallest key in the tree greater than or equal to {@code key}
     *
     * @throws NoSuchElementException if there is no such key
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    public Key ceiling(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("argument to ceiling() is null");
        }
        if (isEmpty()) {
            throw new NoSuchElementException("calls ceiling() with empty tree");
        }
        Node<Key> x = ceiling(root, key);
        if (x == null) {
            throw new NoSuchElementException("argument to ceiling() is too small");
        } else {
            return x.key;
        }
    }

    // the smallest key in the subtree rooted at x greater than or equal to the given key
    private Node<Key> ceiling(Node<Key> x, Key key) {
        while (true) {
            if (x == null) {
                return null;
            }
            int cmp = key.compareTo(x.key);
            if (cmp == 0) {
                return x;
            }
            if (cmp > 0) {
                x = x.right;
                continue;
            }
            Node<Key> t = ceiling(x.left, key);
            if (t != null) {
                return t;
            } else {
                return x;
            }
        }
    }
}
