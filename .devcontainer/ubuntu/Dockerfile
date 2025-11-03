FROM mcr.microsoft.com/devcontainers/base:ubuntu
RUN apt update && apt install -y \
    autotools-dev \
    autoconf \
    automake \
    libtool \
    make \
    tar \
    libaio-dev \
    libssl-dev \
    libapr1-dev \
    lksctp-tools \
    gcc \
    htop \
    strace \
    cmake \
    ninja-build \
    perl \
    golang

USER vscode
RUN curl -s "https://get.sdkman.io" | bash
ARG java_version="25-zulu"
ENV JAVA_VERSION=$java_version
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
    yes | sdk install java $JAVA_VERSION && \
    rm -rf $HOME/.sdkman/archives/* && \
    rm -rf $HOME/.sdkman/tmp/*"
RUN echo 'export JAVA_HOME="$HOME/.sdkman/candidates/java/current"' >> ~/.bashrc
RUN echo 'PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
RUN . "$HOME/.cargo/env"
