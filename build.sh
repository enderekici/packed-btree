#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
jdk_dir="${BBTREE_JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
mkdir -p "$project_dir/.deps" "$project_dir/build/classes" "$project_dir/build/generated"
fetch() {
  local coordinate="$1"
  local artifact="$project_dir/.deps/${coordinate##*/}"
  if [ ! -f "$artifact" ]; then
    curl --fail --location --silent --show-error \
      "https://repo.maven.apache.org/maven2/$coordinate" -o "$artifact.part"
    mv "$artifact.part" "$artifact"
  fi
}
fetch org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar
fetch org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar
fetch net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar
fetch org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar
fetch it/unimi/dsi/fastutil/8.5.15/fastutil-8.5.15.jar
fetch org/openjdk/jol/jol-core/0.17/jol-core-0.17.jar
classpath="$project_dir/.deps/*"
"$jdk_dir/bin/javac" --release 25 -cp "$classpath" \
  -processorpath "$project_dir/.deps/jmh-generator-annprocess-1.37.jar:$project_dir/.deps/jmh-core-1.37.jar" \
  -s "$project_dir/build/generated" -d "$project_dir/build/classes" \
  "$project_dir"/src/main/java/bench/*.java "$project_dir"/src/test/java/bench/*.java
"$jdk_dir/bin/jar" --create --file "$project_dir/build/bbtree-java.jar" \
  -C "$project_dir/build/classes" .
"$jdk_dir/bin/java" -Xmx1g -ea -cp "$project_dir/build/classes:$classpath" bench.LongBTreeTest
shasum -a 256 "$project_dir"/.deps/*.jar > "$project_dir/build/dependencies.sha256"
"$jdk_dir/bin/java" -version
