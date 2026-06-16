# Protocol Runtime Operations Runbook

This runbook covers the standalone collector operations surface introduced in
the `0.16.0` line. It is intentionally app-owned: use package commands,
status exports, management snapshots, and logs before changing code or adding
external supervisors.

## First Evidence To Collect

Run these commands from the unpacked package directory with the same JDK and
configuration used by production:

```sh
bin/protocol-runtime java-check
bin/protocol-runtime version
bin/protocol-runtime verify-package
bin/protocol-runtime validate
bin/protocol-runtime self-check
bin/protocol-runtime hot-check
bin/protocol-runtime status
```

When management is enabled, also collect:

```sh
curl -fsS http://127.0.0.1:8081/health
curl -fsS http://127.0.0.1:8081/readiness
curl -fsS http://127.0.0.1:8081/status
```

If token access is enabled, add the configured authorization header. Do not
paste management tokens into issue reports or logs.

## Self-Check

`self-check` does not bind listener ports or mutate the running collector. It
prints JSON evidence for:

- selected Java runtime and version
- package metadata, package layout paths, and package integrity status
- config file, profile, validation errors, and config checksum
- runtime directory readability and writability
- listener configuration and bind-readiness posture
- sink type, file sink path, and rotation settings
- management host, port, paths, access mode, token presence, request logging,
  and health history capacity
- backpressure policy

Use it before first start, after upgrades, and during support triage.

## Hot-Check Without Hot-Reload

`hot-check` hashes the configured properties file, re-runs validation, compares
against a local baseline file, and reports whether a restart is required. It
never changes the running collector configuration.

Expected statuses:

| Status | Meaning |
| --- | --- |
| `BASELINE_CREATED` | No baseline existed; the current valid config became the comparison point. |
| `UNCHANGED` | Config hash matches the baseline. |
| `CHANGED_RESTART_REQUIRED` | Config changed and validates; restart is required to apply it. |
| `INVALID_CONFIG` | Config changed or current config is invalid; fix validation errors before restart. |
| `CONFIG_UNAVAILABLE` | Config file could not be read or hashed. |

The default package baseline file is `run/config.hotcheck.properties`. Override
it with `--hot-check-baseline FILE` or `HOT_CHECK_BASELINE`.

## Failure Recovery

| Incident | Recovery path |
| --- | --- |
| Stale PID | Confirm the PID is not running, preserve the old PID file if needed for audit, then remove only the stale PID file and restart. |
| Port conflict | Run `validate`, inspect `self-check` listener ports, check local processes, then restart with a free port or stop the conflicting process. |
| Sink path failure | Run `self-check`, confirm the file sink parent directory exists and is writable, fix ownership or path, then dry-run before restart. |
| Config error | Run `validate` and `hot-check`, fix reported keys, rerun both commands, then restart only after validation passes. |
| Management token error | Confirm management access mode and token presence from `self-check`; collect 401/403 status without logging token values. |
| Parse failures | Collect `status`, management `/status`, file sink records, and logs; inspect `lastParseFailure` and payload preview. |
| Backpressure | Collect `status`, management `/status`, and logs; inspect backpressure counters, decisions, and sink failure counters. |
| Checksum mismatch | Re-download artifact and checksum from the same version, rerun `verify-package`, and do not start the package until it passes. |
| Partial extraction | Verify archive checksum, unpack into an empty directory, then rerun `verify-package` and `self-check`. |
| Interrupted upgrade | Stop any running process, preserve old/new logs, switch symlink back to the last known good package, run `version`, `verify-package`, `validate`, `dry-run`, and `self-check`, then start. |
| Rollback validation | After rollback, run `java-check`, `version`, `verify-package`, `validate`, `dry-run`, `self-check`, `status`, and management health/readiness when enabled. |

## Production Issue Diagnostics Flow

1. Record artifact coordinates, package filename, `package.properties`, and
   `bin/protocol-runtime version`.
2. Confirm Java with `java-check`.
3. Confirm archive and unpacked package with `verify-package`.
4. Run `validate`, `dry-run`, `self-check`, and `hot-check`.
5. Export or read `status` from the configured status file.
6. Query management health/readiness/status if enabled.
7. Collect `logs/protocol-runtime.log`, the configured PID file, and any
   startup stdout/stderr wrapper logs.
8. For parsing or sink incidents, collect the latest file sink entries and
   status metrics, but avoid sharing secrets or full payloads unless approved.
9. For upgrade incidents, preserve both old and new package directories and
   compare config templates before editing operator-owned config.

## Verification Commands

Repository maintainers should keep these smoke checks passing:

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-distribution-package.sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-release-artifact.sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-long-running.sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-release-artifact-regression.sh
```

`RUN_SECONDS` can extend the long-running smoke window for manual soak tests.
The default stays short enough for normal development and CI-style checks.
