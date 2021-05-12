#!/bin/bash
set -e

if [ "$#" -ne 3 ]; then
    echo "Expected release.properties file, repository name and branch"
    exit 1
fi

TAG=$(grep scm.tag= "$1" | cut -d'=' -f2)
git remote set-url origin git@github.com:"$2".git
git fetch
git checkout "$3"
./mvnw -B --file pom.xml release:rollback
git push origin :"$TAG"
