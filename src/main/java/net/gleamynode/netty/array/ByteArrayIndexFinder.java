/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.array;


/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public interface ByteArrayIndexFinder {

    boolean find(ByteArray array, int guessedIndex);

    static ByteArrayIndexFinder NUL = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            return array.get8(guessedIndex) == 0;
        }
    };

    static ByteArrayIndexFinder NOT_NUL = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            return array.get8(guessedIndex) != 0;
        }
    };

    static ByteArrayIndexFinder CR = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            return array.get8(guessedIndex) == '\r';
        }
    };

    static ByteArrayIndexFinder NOT_CR = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            return array.get8(guessedIndex) != '\r';
        }
    };

    static ByteArrayIndexFinder LF = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            return array.get8(guessedIndex) == '\n';
        }
    };

    static ByteArrayIndexFinder NOT_LF = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            return array.get8(guessedIndex) != '\n';
        }
    };

    static ByteArrayIndexFinder CRLF = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            byte b = array.get8(guessedIndex);
            return b == '\r' || b == '\n';
        }
    };

    static ByteArrayIndexFinder NOT_CRLF = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            byte b = array.get8(guessedIndex);
            return b != '\r' && b != '\n';
        }
    };

    static ByteArrayIndexFinder LINEAR_WHITESPACE = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            byte b = array.get8(guessedIndex);
            return b == ' ' || b == '\t';
        }
    };

    static ByteArrayIndexFinder NOT_LINEAR_WHITESPACE = new ByteArrayIndexFinder() {
        public boolean find(ByteArray array, int guessedIndex) {
            byte b = array.get8(guessedIndex);
            return b != ' ' && b != '\t';
        }
    };
}
