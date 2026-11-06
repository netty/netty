# Using the docker images

```
cd /path/to/netty/
```

## centos 6 with java 8

```
docker compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-6.18.yaml run test
```

## centos 6 with java 11

```
docker compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-6.111.yaml run test
```

## aarch64 cross compile for transport-native-epoll on X86_64

```
docker compose -f docker/docker-compose.yaml run cross-compile-aarch64-build
```
The default version of aarch64 gcc is `4.9-2016.02`. Update the parameter `gcc_version` in `docker-compose.yaml` to use a version you want.

## riscv64 cross compile for transport-native-epoll on X86_64

```
docker compose -f docker/docker-compose.ubuntu-20.04.yaml run cross-compile-riscv64-build
```

etc, etc

## Pinned toolchain downloads

The Dockerfiles verify downloaded toolchains (SDKMAN! installer, `rustup-init`, ARM GCC) against a pinned SHA-256 before using them.
SDKMAN! does not version its installer script, so if a docker build fails with a checksum mismatch for `sdkman-install.sh`, review the new script and refresh the pinned hash in all Dockerfiles with:

```
docker/update-sdkman-sha.sh
```

Use `docker/update-sdkman-sha.sh --check` to only verify whether the pinned hashes are still current.
