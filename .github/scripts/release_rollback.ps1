# ----------------------------------------------------------------------------
# Copyright 2021 The Netty Project
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

$file=$args[0]
$repository=$args[1]
$branch=$args[2]
$line = Select-String -Path $file -Pattern "scm.tag="
$tag = ($line -split "=")[1]
$url = "git@github.com:" + $repository + ".git"
git remote set-url origin $url
git fetch
git checkout $branch
mvn -B --file pom.xml release:rollback
git push origin :$tag
