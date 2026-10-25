# Third-party material

The Maven Wrapper scripts (`mvnw` and `mvnw.cmd`) are from Apache Maven Wrapper
3.3.4 and are licensed under Apache License 2.0. Their original copyright and
license headers are retained. The license text is in `.mvn/wrapper/LICENSE`.

JMH, JOL, fastutil, jopt-simple and Commons Math are dependencies of the separate
benchmark module. Its shaded JAR preserves the dependency license and notice
resources. They are not dependencies of the library artifact.

The B-tree implementation was written for this project. The BBTree project is
credited as design inspiration; its Rust implementation is not included.
