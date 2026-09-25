# Module Boundaries and Ownership Map

## Scope

This document defines current architecture boundaries and ownership responsibilities for the repository.

Current Gradle module inventory:

- :app

The repository is still single-module at Gradle level. Boundaries are currently enforced at package/layer level.

## Enforced Boundaries (Current)

Source of truth for automated checks:

- scripts/check_architecture_boundaries.sh

Rules currently enforced in CI:

1. Core packages must not import UI/legacy layers.
2. AI core package must not import Activities.

Current guarded package roots:

- app/src/main/java/pro/ascode/featureflags
- app/src/main/java/pro/ascode/metrics
- app/src/main/java/pro/ascode/ai

## Ownership Map

| Area | Primary ownership group | Guarded package roots | Notes |
|---|---|---|---|
| Feature flags and rollout controls | Core Platform | com.ascode.android.featureflags | Must remain UI-independent |
| Metrics, KPI, and release gates | Core Platform | com.ascode.android.metrics | Must remain UI-independent |
| AI core orchestration | AI Platform | io.ascode.android | Must not depend on Activities |
| Editor and LSP integrations | Editor Platform | com.ascode.android.lsp, editor activities | Feature-flagged rollout |
| Debugger and profiling foundations | Runtime Tooling | com.ascode.android.debugger | Keep deterministic and thread-safe contracts |
| Plugin runtime and security | Plugin Runtime | com.ascode.android.plugins | Security scoring and static analysis chain |

## Ownership Responsibilities

1. Any boundary change requires updates in scripts/check_architecture_boundaries.sh and corresponding tests/docs.
2. Any new package under com.ascode.android should be mapped to a primary ownership group here.
3. Boundary regressions are release blockers in CI.

## Next Increment

When moving to multi-module Gradle structure, this map should be extended with:

- module-level owners
- allowed dependency matrix (module -> module)
- mandatory CODEOWNERS alignment
