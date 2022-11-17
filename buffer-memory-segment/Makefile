.PHONY: image test dbg clean build rebuild
.DEFAULT_GOAL := build

image:
	docker build $(DOCKER_BUILD_OPTS) --tag netty-incubator-buffer:build .

test: image
	docker run --rm --name build-container netty-incubator-buffer:build

dbg:
	docker create --name build-container-dbg --entrypoint /bin/bash -t netty-incubator-buffer:build
	docker start build-container-dbg
	docker exec -it build-container-dbg bash

clean:
	docker rm -fv build-container-dbg
	docker rm -fv build-container

clean-layer-cache:
	docker builder prune -f -a

build: image
	docker create --name build-container netty-incubator-buffer:build
	mkdir -p target/container-output
	docker start -a build-container || (docker cp build-container:/home/build target/container-output && false)
	docker wait build-container || (docker cp build-container:/home/build target/container-output && false)
	docker cp build-container:/home/build/target .
	docker rm build-container

rebuild: clean clean-layer-cache build
