# Release Guide

This project publishes JDK 21 runtime artifacts to Maven Central through the
Central Portal.

## Current Release Scope

The latest published runtime release is `0.2.0`.

The current development line is `0.3.0-SNAPSHOT`. The published `0.2.0` release includes:

- `io.github.qbsstg:protocol-runtime`
- `io.github.qbsstg:runtime-core`
- `io.github.qbsstg:runtime-protocol-iec104`
- `io.github.qbsstg:runtime-ingress-tcp-netty`
- `io.github.qbsstg:runtime-app`

`runtime-smoke-tests` is test-only and is configured with
`maven.deploy.skip=true`, so it remains a repository verification module rather
than a published dependency.

The readiness decision is documented in
[`release-readiness-0.2.0.md`](release-readiness-0.2.0.md). The `0.3.0`
roadmap is maintained in [`roadmap-0.3.0.md`](roadmap-0.3.0.md), draft release
notes are maintained in [`release-notes-0.3.0.md`](release-notes-0.3.0.md),
and release-readiness audit work is tracked in
[`release-readiness-0.3.0.md`](release-readiness-0.3.0.md).

`0.3.0` is not a release branch yet. Main should remain on
`0.3.0-SNAPSHOT` until the production-hardening scope has passed local and
GitHub Actions verification.

## Prerequisites

1. Verify the `io.github.qbsstg` namespace in the Central Portal.
2. Generate a Central Portal user token.
3. Create or choose a GPG key for artifact signing.
4. Publish the public GPG key to a supported key server.
5. Keep Central credentials and GPG secrets outside this repository.

The Maven server id used by this project is `central`.

Example `~/.m2/settings.xml` server entry:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_TOKEN_USERNAME}</username>
      <password>${env.CENTRAL_TOKEN_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

## Local Checks

Run the normal verification before release work:

```bash
mvn -q verify
```

The build should generate these artifacts for published jar modules:

- main jar
- sources jar
- Javadoc jar

To verify the release profile wiring without requiring GPG signing or Central
credentials, run:

```bash
mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy
```

This is only a profile smoke check. It must not replace the signed dry run
before a real release.

## Signed Dry Run

Use the `central-release` profile to sign artifacts. The following command keeps
publishing disabled, so it can be used to verify signing and Central bundle
generation without publishing an immutable deployment:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

This command requires local GPG signing to work. Prefer `gpg-agent` for
passphrase handling so secrets do not enter shell history or project files.

## Manual Publishing Flow

The default release profile creates a user-managed Central deployment:

```bash
mvn -Pcentral-release clean deploy
```

By default:

- `autoPublish` is `false`
- the deployment is uploaded and validated
- final publishing is completed manually in the Central Portal or through the
  Central Publisher API

This is intentional for early runtime releases because it leaves a review point
before artifacts become immutable on Maven Central.

## Release Checklist

1. Confirm `main` is clean and CI is green.
2. Confirm the release-readiness note for the target version is current.
3. Update versions from the target `-SNAPSHOT` version to the final release
   version.
4. Confirm README and module boundary docs label module stability accurately.
5. Run `mvn -q verify`.
6. Run the Central release profile smoke check with publishing disabled.
7. Run the signed dry run with publishing disabled.
8. Tag the release commit.
9. Run the manual publishing command.
10. Review and publish the validated deployment in the Central Portal or
    through the Central Publisher API.
11. Publish GitHub release notes from the target release-note draft.
12. Move versions to the next `-SNAPSHOT`.

## References

- Central Portal Maven publishing:
  https://central.sonatype.org/publish/publish-portal-maven/
- Central Portal token generation:
  https://central.sonatype.org/publish/generate-portal-token/
- Central GPG requirements:
  https://central.sonatype.org/publish/requirements/gpg/
