
** Create a docker image **
```
docker build -f Dockerfile-netty-centos6 . -t netty-centos6
```

** Using the image **

```
cd /path/to/netty/
```

```
docker run -it -v ~/.m2:/root/.m2 -v ~/.ssh:/root/.ssh -v ~/.gnupg:/root/.gnupg -v `pwd`:/code -w /code netty-centos6 bash
```
