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
