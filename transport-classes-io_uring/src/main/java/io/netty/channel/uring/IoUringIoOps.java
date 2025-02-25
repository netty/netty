/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.IoOps;

/**
 * {@link IoOps} for implementation for
 * <a href="https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h">Io_uring</a>.
 */
public final class IoUringIoOps implements IoOps {

    private final byte opcode;
    private final byte flags;
    private final short ioPrio;
    private final int fd;
    private final long union1;
    private final long union2;
    private final int len;
    private final int union3;
    private final short data;
    private final short personality;
    private final short union4;
    private final int union5;
    private final long union6;

    /**
     * Create a new instance which represents the {@code io_uring_sqe} struct.
     *
     * <pre>{@code
     *  struct io_uring_sqe {
     *      __u8    opcode;    // type of operation for this sqe
     *      __u8    flags;     // IOSQE_ flags
     *      __u16   ioprio;    // ioprio for the request
     *      __s32   fd;        // file descriptor to do IO on
     *
     *      union {            // union1
     *          __u64 off;     // offset into file
     *          __u64 addr2;
     *          struct {
     *              __u32 cmd_op;
     *              __u32 __pad1;
     *          };
     *      };
     *
     *      union {             // union2
     *          __u64 addr;    // pointer to buffer or iovecs
     *          __u64 splice_off_in;
     *          struct {
     *              __u32 level;
     *              __u32 optname;
     *          };
     *      };
     *      __u32 len;        // buffer size or number of iovecs
     *
     *      union {           // union3
     *          __kernel_rwf_t rw_flags;
     *          __u32 fsync_flags;
     *          __u16 poll_events;    // compatibility
     *          __u32 poll32_events; // word-reversed for BE
     *          __u32 sync_range_flags;
     *          __u32 msg_flags;
     *          __u32 timeout_flags;
     *          __u32 accept_flags;
     *          __u32 cancel_flags;
     *          __u32 open_flags;
     *          __u32 statx_flags;
     *          __u32 fadvise_advice;
     *          __u32 splice_flags;
     *          __u32 rename_flags;
     *          __u32 unlink_flags;
     *          __u32 hardlink_flags;
     *          __u32 xeattr_flags;
     *          __u32 msg_ring_flags;
     *          __u32 uring_cmd_flags;
     *          __u32 waitid_flags;
     *          __u32 futex_flags;
     *          __u32 install_fd_flags;
     *          __u32 nop_flags;
     *      };
     *      __u64 user_data;    // data to be passed back at completion time
     *                          // pack this to avoid bogus arm OABI complaints
     *
     *      union {             // union4
     *
     *          // index into fixed buffers, if used
     *          __u16 buf_index;
     *          // for grouped buffer selection
     *          __u16 buf_group;
     *      }__attribute__((packed));
     *      // personality to use, if used
     *      __u16 personality;
     *
     *      union {            // union5
     *
     *          __s32 splice_fd_in;
     *          __u32 file_index;
     *          __u32 optlen;
     *          struct {
     *              __u16 addr_len;
     *              __u16 __pad3[ 1];
     *          };
     *      };
     *
     *      union {           // union6
     *
     *          struct {
     *              __u64 addr3;
     *              __u64 __pad2[ 1];
     *          };
     *          __u64 optval;
     *          //
     *          // If the ring is initialized with IORING_SETUP_SQE128, then
     *          // this field is used for 80 bytes of arbitrary command data
     *          __u8 cmd[ 0];
     *      };
     *  };
     * }
     * </pre>
     */
    public IoUringIoOps(byte opcode, byte flags, short ioPrio, int fd, long union1, long union2, int len, int union3,
                        short data, short union4, short personality, int union5, long union6) {
        this.opcode = opcode;
        this.flags = flags;
        this.ioPrio = ioPrio;
        this.fd = fd;
        this.union1 = union1;
        this.union2 = union2;
        this.len = len;
        this.union3 = union3;
        this.data = data;
        this.union4 = union4;
        this.personality = personality;
        this.union5 = union5;
        this.union6 = union6;
    }

    byte opcode() {
        return opcode;
    }

    byte flags() {
        return flags;
    }

    short ioPrio() {
        return ioPrio;
    }

    int fd() {
        return fd;
    }

    long union1() {
        return union1;
    }

    long union2() {
        return union2;
    }

    int len() {
        return len;
    }

    int union3() {
        return union3;
    }

    short data() {
        return data;
    }

    short personality() {
        return personality;
    }

    short union4() {
        return union4;
    }

    int union5() {
        return union5;
    }

    long union6() {
        return union6;
    }

    @Override
    public String toString() {
        return "IOUringIoOps{" +
                "opcode=" + opcode +
                ", flags=" + flags +
                ", ioPrio=" + ioPrio +
                ", fd=" + fd +
                ", union1=" + union1 +
                ", union2=" + union2 +
                ", len=" + len +
                ", union3=" + union3 +
                ", data=" + data +
                ", union4=" + union4 +
                ", personality=" + personality +
                ", union5=" + union5 +
                ", union6=" + union6 +
                '}';
    }

    /**
     * Returns a new {@code OP_ASYNC_CANCEL} {@link IoUringIoOps}.
     *
     * @param flags     the flags.
     * @param userData  the user data that identify a previous submitted {@link IoUringIoOps} that should be cancelled.
     *                  The value to use here is returned by {@link io.netty.channel.IoRegistration#submit(IoOps)}.
     * @param data      the data
     * @return          ops.
     */
    static IoUringIoOps newAsyncCancel(byte flags, long userData, short data) {
        // Best effort to cancel the
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L679
        return new IoUringIoOps(Native.IORING_OP_ASYNC_CANCEL, flags, (short) 0, -1, 0, userData, 0, 0,
                data, (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_CLOSE} {@link IoUringIoOps}.
     *
     * @param fd        the filedescriptor
     * @param flags     the flags.
     * @param data      the data
     * @return          ops.
     */
    static IoUringIoOps newClose(int fd, byte flags, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L764
        return new IoUringIoOps(Native.IORING_OP_CLOSE, flags, (short) 0, fd, 0L, 0L, 0, 0, data,
                (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_POLL_ADD} {@link IoUringIoOps}.
     *
     * @param fd        the filedescriptor
     * @param flags     the flags.
     * @param mask      the mask.
     * @param len       the len.
     * @param data      the data.
     * @return          ops.
     */
    static IoUringIoOps newPollAdd(int fd, byte flags, int mask, int len, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L554
        return new IoUringIoOps(Native.IORING_OP_POLL_ADD, flags, (short) 0, fd, 0L, 0L, len, mask, data,
                (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_SENDMSG} {@link IoUringIoOps}.
     *
     * @param fd        the filedescriptor
     * @param flags     the flags.
     * @param msgFlags  the msg flags.
     * @param data      the data
     * @return          ops.
     */
    static IoUringIoOps newSendmsg(int fd, byte flags, int msgFlags, long address, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L539
        return new IoUringIoOps(Native.IORING_OP_SENDMSG, flags, (short) 0, fd, 0L, address, 1, msgFlags, data,
                (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_CONNECT} {@link IoUringIoOps}.
     *
     * @param fd                    the filedescriptor
     * @param flags                 the flags.
     * @param remoteMemoryAddress   the memory address of the sockaddr_storage.
     * @param data                  the data
     * @return                      ops.
     */
    static IoUringIoOps newConnect(int fd, byte flags, long remoteMemoryAddress, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L695
        return new IoUringIoOps(Native.IORING_OP_CONNECT, flags, (short) 0, fd, Native.SIZEOF_SOCKADDR_STORAGE,
                remoteMemoryAddress, 0, 0, data, (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_ACCEPT} {@link IoUringIoOps}.
     *
     * @param fd                                    the filedescriptor
     * @param flags                                 the flags.
     * @param acceptFlags                           the flags for ACCEPT itself
     * @param ioPrio                                io_prio
     * @param acceptedAddressMemoryAddress          the memory address of the sockaddr_storage.
     * @param acceptedAddressLengthMemoryAddress    the memory address of the length that will be updated once a new
     *                                              connection was accepted.
     * @param data                                  the data
     * @return                                      ops.
     */
    static IoUringIoOps newAccept(int fd, byte flags, int acceptFlags, short ioPrio, long acceptedAddressMemoryAddress,
                                         long acceptedAddressLengthMemoryAddress, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L625
        return new IoUringIoOps(Native.IORING_OP_ACCEPT, flags, ioPrio, fd, acceptedAddressLengthMemoryAddress,
                acceptedAddressMemoryAddress, 0, acceptFlags, data, (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_WRITEV} {@link IoUringIoOps}.
     *
     * @param fd                                    the filedescriptor
     * @param flags                                 the flags.
     * @param writevFlags                           the writev flags.
     * @param memoryAddress                         the memory address of the io_vec array.
     * @param length                                the length of the io_vec array.
     * @param data                                  the data
     * @return                                      ops.
     */
    static IoUringIoOps newWritev(int fd, byte flags, int writevFlags, long memoryAddress,
                                         int length, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L500
        return new IoUringIoOps(Native.IORING_OP_WRITEV, flags, (short) 0, fd,
                0, memoryAddress, length, writevFlags, data, (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_WRITE} {@link IoUringIoOps}.
     *
     * @param fd                                    the filedescriptor
     * @param flags                                 the flags.
     * @param writeFlags                            the write flags.
     * @param memoryAddress                         the memory address of the buffer
     * @param length                                the length of the buffer.
     * @param data                                  the data
     * @return                                      ops.
     */
    static IoUringIoOps newWrite(
            int fd, byte flags, int writeFlags, long memoryAddress, int length, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L794
        return new IoUringIoOps(Native.IORING_OP_WRITE, flags, (short) 0, fd,
                0, memoryAddress, length, writeFlags, data, (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_RECV} {@link IoUringIoOps}.
     *
     * @param fd                                    the filedescriptor
     * @param flags                                 the flags.
     * @param ioPrio                                the ioPrio.
     * @param recvFlags                             the recv flags.
     * @param memoryAddress                         the memory address of the buffer
     * @param length                                the length of the buffer.
     * @param data                                  the data
     * @return                                      ops.
     */
    static IoUringIoOps newRecv(
            int fd, byte flags, short ioPrio, int recvFlags, long memoryAddress, int length, short data) {
        return newRecv(fd, flags, ioPrio, recvFlags, memoryAddress, length, data, (short) 0);
    }

    static IoUringIoOps newRecv(
            int fd, byte flags, short ioPrio, int recvFlags, long memoryAddress, int length, short data, short bid) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L898
        return new IoUringIoOps(Native.IORING_OP_RECV, flags, ioPrio, fd,
                0, memoryAddress, length, recvFlags, data, bid, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_RECVMSG} {@link IoUringIoOps}.
     *
     * @param fd                                    the filedescriptor
     * @param flags                                 the flags.
     * @param msgFlags                              the recvmsg flags.
     * @param memoryAddress                         the memory address of the msghdr struct
     * @param data                                  the data
     * @return                                      ops.
     */
    static IoUringIoOps newRecvmsg(int fd, byte flags, int msgFlags, long memoryAddress, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L523
        return new IoUringIoOps(
                Native.IORING_OP_RECVMSG, flags, (short) 0, fd, 0L, memoryAddress, 1, msgFlags, data,
                (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_SEND} {@link IoUringIoOps}.
     *
     * @param fd                                    the filedescriptor
     * @param flags                                 the flags.
     * @param sendFlags                             the send flags.
     * @param memoryAddress                         the memory address of the buffer.
     * @param length                                the length of the buffer.
     * @param data                                  the data
     * @return                                      ops.
     */
    static IoUringIoOps newSend(
            int fd, byte flags, int sendFlags, long memoryAddress, int length, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L839
        return new IoUringIoOps(Native.IORING_OP_SEND, flags, (short) 0, fd,
                0, memoryAddress, length, sendFlags, data, (short) 0, (short) 0, 0, 0);
    }

    /**
     * Returns a new {@code OP_SHUTDOWN} {@link IoUringIoOps}.
     *
     * @param fd                                    the filedescriptor
     * @param flags                                 the flags.
     * @param how                                   how the shutdown will be done.
     * @param data                                  the data
     * @return                                      ops.
     */
    static IoUringIoOps newShutdown(int fd, byte flags, int how, short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L1023
        return new IoUringIoOps(Native.IORING_OP_SHUTDOWN, flags, (short) 0, fd, 0, 0, how, 0, data,
                (short) 0, (short) 0, 0, 0);
    }

    /**
     *
     * Returns a new {@code OP_SPLICE} {@link IoUringIoOps}.
     *
     * @param fd_in                                     the filedescriptor
     * @param off_in                                    the filedescriptor offset
     * @param fd_out                                    the filedescriptor
     * @param off_out                                   the filedescriptor offset
     * @param nbytes                                    splice bytes
     * @param splice_flags                              the flag
     * @param data                                      the data
     * @return                                          ops.
     */
    static IoUringIoOps newSplice(int fd_in, long off_in,
                                         int fd_out, long off_out,
                                         int nbytes,
                                         int splice_flags,
                                         short data) {
        // See https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L454
        return new IoUringIoOps(
                Native.IORING_OP_SPLICE, (byte) 0, (short) 0, fd_out, off_out, off_in,
                nbytes, splice_flags, data, (short) 0, (short) 0, fd_in, 0
        );
    }
}
