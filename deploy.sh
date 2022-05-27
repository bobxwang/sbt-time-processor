#!/bin/sh

mvn deploy:deploy-file -X \
-DgroupId=com.bob.xxx \
-DartifactId=process \
-Dversion=0.0.1 \
-Dpackaging=jar \
-Dfile=./target/scala-2.12/process-0.0.1.jar \
-DrepositoryId=maven-xxx \
-Durl=http://maven.xxx.com/repository/maven-releases