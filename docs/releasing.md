# Releasing to Maven Central

The published artifacts in the first release are the parent POM
`io.github.enderekici:packed-btree-parent` and core library
`io.github.enderekici:packed-btree`. The benchmark and optional SIMD modules
are not selected by the release workflow. The project uses the Apache License
2.0; see [`LICENSE`](../LICENSE).
The local packaging and build evidence is in the
[release validation receipt](release-validation.md).

## One-time setup

1. Verify the `io.github.enderekici` namespace in the Sonatype Central Portal.
2. Create a Central user token and configure the repository secrets
   `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` with its token name and password.
3. Create a dedicated OpenPGP signing key, publish its public key to a key
   server, and configure the armored private key as `GPG_SIGNING_KEY` and its
   passphrase as `GPG_PASSPHRASE`. Do not reuse a personal key. Keep the
   private key and passphrase only in the GitHub repository secrets.

The workflow requires all four secrets and fails before building if any are
missing. It imports exactly one secret primary key. Normal CI and developer
builds do not activate the `central-release` profile and do not sign artifacts.

## Release flow

From the repository's `main` branch, run **Publish to Maven Central** from
GitHub Actions and enter a version matching `0.x.y` or `0.x.y-rcN`. The workflow
rejects SNAPSHOT versions and versions whose Git tag already exists. Runs
dispatched from other refs are skipped. It changes versions only in its
disposable workflow checkout, runs a
JDK 17 `clean verify` for the parent and core library, then uploads the signed
bundle to Central for validation. It does not publish automatically. Review
the deployment in the Central Portal and publish it there after validation;
then create the matching Git tag on the verified main commit. Published
versions are immutable, so do not reuse or overwrite a release version.

The GitHub Actions workflow only runs its release job on `main`; a run started
from another ref is skipped. No tag is created by the workflow before the
Portal publish step.

## Local bundle check

Use a disposable checkout and a temporary test signing key. This generates a
signed bundle locally but skips upload; it does not use Central credentials or
the production signing key. The result tests packaging and signatures against
the temporary public key, not Central's account or validation service.

```bash
git worktree add --detach /tmp/packed-btree-central-dryrun origin/main
cd /tmp/packed-btree-central-dryrun
./mvnw --batch-mode org.codehaus.mojo:versions-maven-plugin:2.22.0:set \
  -DnewVersion=0.1.0-rc1 -DprocessAllModules=true -DgenerateBackupPoms=false
export GNUPGHOME="$(mktemp -d /tmp/packed-btree-test-gnupg.XXXXXX)"
chmod 700 "$GNUPGHOME"
export GPG_PASSPHRASE="$(openssl rand -hex 24)"
printf '%s\n' "$GPG_PASSPHRASE" | gpg --batch --pinentry-mode loopback --passphrase-fd 0 \
  --quick-generate-key 'Local bundle validation <noreply@example.invalid>' rsa3072 sign 1d
```

For the Maven invocation, set `GPG_PASSPHRASE` to the temporary test-key
passphrase in the shell environment and use:

```bash
./mvnw --batch-mode -Pcentral-release \
  -pl library -am clean deploy -Dcentral.skipUpload=true
```

Locate `target/central-publishing/central-bundle.zip`, inspect it with
`unzip -l`, extract it into a temporary directory, and run
`gpg --verify file.asc file`
for every artifact signature. The `skipUpload` option creates the bundle and
stops before network upload; it is different from `skipPublishing`, which
excludes artifacts from the bundle. Remove only the disposable checkout and
temporary key material after inspection.

The release profile uses Sonatype's Central Publishing Maven Plugin with
`autoPublish=false`, so upload validation and final publication remain separate
steps. Official references: [Central Maven publishing](https://central.sonatype.org/publish/publish-portal-maven/),
[Central publishing requirements](https://central.sonatype.org/publish/requirements/),
[Apache Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/).
