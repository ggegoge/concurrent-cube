#!/bin/sh

# Run junit tests from the commandline. You need to have the console standalone
# launcher jar saved here.

echo Compling sources...

javac -d target -cp target:junit-platform-console-standalone-1.8.1.jar concurrentcube/*.java

echo Running JUNIT tests.

java -jar junit-platform-console-standalone-1.8.1.jar --class-path target \
     --select-class concurrentcube.CubeTest
