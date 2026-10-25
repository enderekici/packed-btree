# Release configuration validation

The configuration and artifact checks below ran against `fcf51e4`. The
follow-up commit adding this receipt changes documentation only.

## Build gates

With JDK 17 selected, `./mvnw --batch-mode --no-transfer-progress clean verify`
passed with 29 tests (25 library, 4 benchmark). With JDK 25 selected,
`./mvnw --batch-mode --no-transfer-progress -Psimd clean verify` passed with 32
tests (25 library, 4 benchmark, 3 SIMD). Both commands exited 0. The core JAR
reports only `java.base` in `jdeps --multi-release 17 --print-module-deps`.

## Local signed bundle

In a disposable checkout, the project version was changed to `0.1.0-rc1` and
this command was run with JDK 17, a temporary signing key, and the Central
plugin's upload disabled:

```bash
./mvnw --batch-mode --no-transfer-progress -Pcentral-release \
  -pl library -am clean deploy -Dcentral.skipUpload=true
```

It exited 0 with 25 passing library tests. The bundle at
`target/central-publishing/central-bundle.zip` contained only the parent POM
and core library POM, binary, sources, and Javadoc artifacts. The bundle had
five detached signatures: parent POM plus the four core artifacts. Each
signature verified with the disposable test key. No benchmark or SIMD
artifacts were staged, and the plugin reported that upload was skipped.

The binary and sources archives contain the Apache 2.0 text at
`META-INF/LICENSE`; the Javadoc archive contains the same text under
`io/github/enderekici/packedbtree/doc-files/LICENSE`. All three files and the
root `LICENSE` have SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.

## Workflow checks and limits

The workflow YAML parsed with Python's PyYAML. The version guard accepted
`0.1.0` and `0.9.12-rc3` and rejected `0.1.0-SNAPSHOT`, `1.0.0`, and
`0.1.0;echo`. The secret-presence guard rejected an intentionally missing
`GPG_PASSPHRASE`. The workflow was not executed on GitHub, and the bundle was
not uploaded to or validated by the Central Portal. A dedicated production
signing key remains a release setup requirement.

Full command output from this local run was kept outside the repository in
`/tmp/packed-btree-central-final-jdk17.log`,
`/tmp/packed-btree-central-final-jdk25-simd.log`, and
`/tmp/packed-btree-central-dryrun.log`.
