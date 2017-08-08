#!/bin/bash

set -e

if [[ ! -z `git status --porcelain` ]] ; then
  echo Dirty working tree. Aborting.
  exit 1
fi

git checkout master

sbt clean fullOptJS

# For some reason we have to compile once again with a changed file to not get
# exceptions at runtime. Maybe it's related to the config files that are being
# compiled into the binary.
echo >> src/main/scala/de/tilltheis/Main.scala
sbt fullOptJS
git checkout src/main/scala/de/tilltheis/Main.scala

git checkout gh-pages

cp target/scala-2.12/slugfest-opt.js app.js
git checkout master index.html

sed -i -- 's/target\/scala-2.12\/slugfest-fastopt.js/app.js/g' index.html

git add app.js index.html
git commit
