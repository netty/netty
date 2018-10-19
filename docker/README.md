# Using the docker images

```
cd /path/to/netty/
```

## centos 6 with java 8

```
docker-compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-6.18.yaml run test
```

## centos 7 with java 9

```
docker-compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-7.19.yaml run test
```

etc, etc
