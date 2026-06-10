# Release Guide

This project publishes JDK 21 runtime artifacts to Maven Central through the
Central Portal.

## Current Release Scope

The latest published runtime release is `0.8.0`.

The current release branch is `0.9.0`; its roadmap is maintained in
[`roadmap-0.9.0.md`](roadmap-0.9.0.md), release notes are maintained in
[`release-notes-0.9.0.md`](release-notes-0.9.0.md), and release-readiness audit
is tracked in [`release-readiness-0.9.0.md`](release-readiness-0.9.0.md).

The published `0.8.0` release includes:

- `io.github.qbsstg:protocol-runtime`
- `io.github.qbsstg:runtime-core`
- `io.github.qbsstg:runtime-protocol-iec104`
- `io.github.qbsstg:runtime-protocol-iec101`
- `io.github.qbsstg:runtime-protocol-iec103`
- `io.github.qbsstg:runtime-protocol-modbus`
- `io.github.qbsstg:runtime-ingress-tcp-netty`
- `io.github.qbsstg:runtime-ingress-http`
- `io.github.qbsstg:runtime-ingress-kafka`
- `io.github.qbsstg:runtime-ingress-mqtt`
- `io.github.qbsstg:runtime-app`

`runtime-smoke-tests` is test-only and remains unsupported as an application
dependency. It is intentionally skipped for Central publishing from `0.4.0`
onward.

The `0.8.0` roadmap is maintained in
[`roadmap-0.8.0.md`](roadmap-0.8.0.md), release notes are maintained
in [`release-notes-0.8.0.md`](release-notes-0.8.0.md). The `0.8.0`
release-readiness audit is tracked in
[`release-readiness-0.8.0.md`](release-readiness-0.8.0.md). The published `0.7.0`
roadmap is maintained in [`roadmap-0.7.0.md`](roadmap-0.7.0.md), release notes
are maintained in [`release-notes-0.7.0.md`](release-notes-0.7.0.md), and the
`0.7.0` release-readiness audit is tracked in
[`release-readiness-0.7.0.md`](release-readiness-0.7.0.md). The published
`0.6.0` roadmap is maintained in
[`roadmap-0.6.0.md`](roadmap-0.6.0.md), release notes are maintained in
[`release-notes-0.6.0.md`](release-notes-0.6.0.md), and the `0.6.0`
release-readiness audit is tracked in
[`release-readiness-0.6.0.md`](release-readiness-0.6.0.md). The previous
published `0.5.0` roadmap is maintained in
[`roadmap-0.5.0.md`](roadmap-0.5.0.md), release notes are maintained in
[`release-notes-0.5.0.md`](release-notes-0.5.0.md), and the `0.5.0`
release-readiness audit is tracked in
[`release-readiness-0.5.0.md`](release-readiness-0.5.0.md). The previous
published `0.4.0` roadmap is maintained in
[`roadmap-0.4.0.md`](roadmap-0.4.0.md), release notes are maintained in
[`release-notes-0.4.0.md`](release-notes-0.4.0.md), and the
`0.4.0` release-readiness audit is tracked in
[`release-readiness-0.4.0.md`](release-readiness-0.4.0.md). The previous
`0.3.0` release notes are maintained in
[`release-notes-0.3.0.md`](release-notes-0.3.0.md), and the `0.3.0`
release-readiness audit is tracked in
[`release-readiness-0.3.0.md`](release-readiness-0.3.0.md).

`0.8.0` was tagged as `v0.8.0`, uploaded in Central deployment
`f2b54d7a-924f-44f2-bbd9-6199fa1514a3`, published, and verified from Maven
Central with an isolated local Maven repository. GitHub release notes are
published at
<https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.8.0>.

`0.7.0` was tagged as `v0.7.0`, uploaded in Central deployment
`64ef1af3-adb0-4cbd-9a84-8bb2214ecc9f`, published, and verified from Maven
Central with an isolated local Maven repository. GitHub release notes are
published at
<https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.7.0>.

`0.6.0` was tagged as `v0.6.0`, uploaded in Central deployment
`7b908e63-6006-4ecb-9b87-d099d89582be`, published, and verified from Maven
Central with an isolated local Maven repository.

`0.5.0` was tagged as `v0.5.0`, uploaded in Central deployment
`7de75e6d-21a3-4fdb-aaef-2a9660ded7d7`, published, and verified from Maven
Central with an isolated local Maven repository.

`0.4.0` was tagged as `v0.4.0`, uploaded in Central deployment
`921e97e5-e002-4498-865f-a3106ed06042`, published, and verified from Maven
Central with an isolated local Maven repository.

`0.3.0` was tagged as `v0.3.0`, uploaded in Central deployment
`eaa2bf69-69d3-416f-9529-550924a33b28`, published, and verified from Maven
Central with an isolated local Maven repository.

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
