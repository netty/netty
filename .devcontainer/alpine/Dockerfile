# Pin alpine version to 3.18, because JetBrains Runtime (OpenJDK distro) requires posix_fallocate64()
# which was removed in 3.19. The real fix is for JBR to use posix_fallocate() without the '64' suffix.
FROM mcr.microsoft.com/devcontainers/base:alpine-3.18
RUN apk -U upgrade && apk add \
    autoconf automake linux-headers libtool make tar libaio-dev openssl-dev apr-dev gcc \
    mandoc man-pages autoconf-doc automake-doc libtool-doc make-doc tar-doc gcc-doc \
    perf perf-bash-completion htop htop-doc strace strace-doc ripgrep ripgrep-doc gcompat \
    openjdk17-jdk openjdk17-doc openjdk17-src maven
# Once Intellij has connected to the devconainer, run `unset LD_LIBRARY_PATH` to fix Java broken by incompatible JBR.
