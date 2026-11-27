#!/usr/bin/env python3
# ----------------------------------------------------------------------------
# Copyright 2026 The Netty Project
#
# The Netty Project licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------
"""
Copies a 64-bit Mach-O object file, or an archive of them, and marks all defined external symbols that don't match
one of the given patterns as private extern (the Mach-O equivalent of hidden visibility).

zig's Mach-O linker does not support -exported_symbol / -exported_symbols_list, so zig-toolchain.sh uses this to
get the same result: private extern symbols still resolve between the inputs of the link, but are not exported by
the resulting dylib.

Usage: macho-hide-symbols.py [--keep <pattern>]... <input> <output>

The patterns use the same wildcards as ld64 (*, ? and [...]) and match the symbol names as they are in the symbol
table, so including the leading underscore (for example _JNI_*).

Only the n_type byte of the symbols changes, so the layout of the file, and with that the symbol table of an archive,
stays the same. Archive members that are not 64-bit Mach-O files (for example the metadata in a Rust rlib) are
copied as they are.
"""
import fnmatch
import struct
import sys

MH_MAGIC_64 = 0xfeedfacf
LC_SYMTAB = 0x2

N_STAB = 0xe0
N_PEXT = 0x10
N_TYPE = 0x0e
N_EXT = 0x01
N_UNDF = 0x0

AR_MAGIC = b"!<arch>\n"
AR_HEADER_SIZE = 60


def hide_symbols(data, offset, size, keep):
    """Marks the symbols of the Mach-O file at data[offset:offset+size] as private extern, in place."""
    if size < 32 or struct.unpack_from("<I", data, offset)[0] != MH_MAGIC_64:
        return 0
    ncmds = struct.unpack_from("<I", data, offset + 16)[0]
    cmd_offset = offset + 32
    hidden = 0
    for _ in range(ncmds):
        cmd, cmdsize = struct.unpack_from("<II", data, cmd_offset)
        if cmdsize < 8 or cmd_offset + cmdsize > offset + size:
            raise ValueError("malformed load command")
        if cmd == LC_SYMTAB:
            symoff, nsyms, stroff, strsize = struct.unpack_from("<IIII", data, cmd_offset + 8)
            if symoff + nsyms * 16 > size or stroff + strsize > size:
                raise ValueError("symbol table out of bounds")
            for i in range(nsyms):
                entry = offset + symoff + i * 16
                n_strx = struct.unpack_from("<I", data, entry)[0]
                n_type = data[entry + 4]
                if n_type & N_STAB or not n_type & N_EXT or n_type & N_PEXT or (n_type & N_TYPE) == N_UNDF:
                    continue
                if n_strx >= strsize:
                    raise ValueError("symbol name out of bounds")
                name_start = offset + stroff + n_strx
                name_end = data.index(b"\0", name_start, offset + stroff + strsize)
                name = data[name_start:name_end].decode("utf-8", "replace")
                if not any(fnmatch.fnmatchcase(name, pattern) for pattern in keep):
                    data[entry + 4] = n_type | N_PEXT
                    hidden += 1
        cmd_offset += cmdsize
    return hidden


def hide_archive_symbols(data, keep):
    """Marks the symbols of all the Mach-O members of the (BSD or GNU) ar archive in data as private extern."""
    hidden = 0
    pos = len(AR_MAGIC)
    while pos + AR_HEADER_SIZE <= len(data):
        header = data[pos:pos + AR_HEADER_SIZE]
        if header[58:60] != b"`\n":
            raise ValueError("malformed archive member header at offset %d" % pos)
        name = header[0:16].decode("ascii", "replace").rstrip()
        member_size = int(header[48:58].decode("ascii").strip())
        member_offset = pos + AR_HEADER_SIZE
        content_offset = member_offset
        if name.startswith("#1/"):
            # BSD long name, which precedes the content of the member
            content_offset += int(name[3:])
        hidden += hide_symbols(data, content_offset, member_offset + member_size - content_offset, keep)
        pos = member_offset + member_size
        if pos % 2:
            pos += 1
    return hidden


def main(argv):
    keep = []
    args = argv[1:]
    while args and args[0] == "--keep":
        if len(args) < 2:
            raise SystemExit("--keep needs a pattern")
        keep.append(args[1])
        args = args[2:]
    if len(args) != 2:
        raise SystemExit(__doc__)
    source, target = args
    with open(source, "rb") as f:
        data = bytearray(f.read())
    if data.startswith(AR_MAGIC):
        hide_archive_symbols(data, keep)
    elif len(data) >= 4 and struct.unpack_from("<I", data, 0)[0] == MH_MAGIC_64:
        hide_symbols(data, 0, len(data), keep)
    else:
        raise SystemExit("%s: not a 64-bit Mach-O object file or archive" % source)
    with open(target, "wb") as f:
        f.write(data)


if __name__ == "__main__":
    main(sys.argv)
