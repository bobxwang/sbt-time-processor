#!/bin/sh

mvn install:install-file -X \
-DgroupId=com.bob.xxx \
-DartifactId=process \
-Dversion=0.0.1 \
-Dpackaging=jar \
-Dfile=./target/scala-2.12/process-0.0.1.jar