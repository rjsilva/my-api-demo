<!--
Sync Impact Report
Version change: 2.0.0 → 2.1.0 (MINOR: new principles and constraints added)
Modified principles: none renamed or redefined
Added sections:
  - VI. Observability (structured logs with correlation ID, metrics, distributed tracing)
  - VII. Resilience for External Dependencies (mandatory timeouts, specified failure behavior
    for database and service calls)
  - Technical Constraints: OpenTelemetry telemetry and local visualization with Grafana
  - Development Workflow: observability/resilience coverage required in plans and reviews
Removed sections: none
Deferred TODOs: none
Rationale for bump: adds new non-negotiable principles without removing or redefining
  existing ones, hence MINOR.
-->

# demo-ia-spec Constitution

## Core Principles

### I. Layered Architecture
The codebase MUST be organized into three explicit layers: controller, service, and
repository. Controllers MUST only handle HTTP concerns (request/response mapping,
validation of input shape) and delegate business logic to services. Services MUST
contain all business logic and MUST NOT access persistence directly. Repositories
MUST be the only layer that talks to the database. Cross-layer shortcuts (e.g.,
controllers calling repositories directly) are NOT allowed.
**Rationale**: A fixed layering keeps responsibilities testable in isolation and
prevents business logic from leaking into HTTP or persistence code.

### II. Test-First Development (NON-NEGOTIABLE)
JUnit 5 tests MUST be written before the corresponding implementation code. The
Red-Green-Refactor cycle MUST be followed: write a failing test, implement the
minimum code to pass it, then refactor. Pull requests that add production code
without a preceding or accompanying test are NOT acceptable.
**Rationale**: TDD gives early feedback on design and guarantees regression coverage
grows with the codebase rather than trailing behind it.

### III. Spec-Driven Development
No implementation work MAY begin before a feature has a spec, a plan, and a tasks
list (via `/speckit-specify`, `/speckit-plan`, `/speckit-tasks`). Code changes MUST
be traceable to an approved task. Ad-hoc coding without these artifacts is NOT
permitted for non-trivial changes.
**Rationale**: Forcing spec → plan → tasks before code keeps intent, design
decisions, and scope visible and reviewable before they are locked into code.

### IV. English Code, Portuguese Documentation
All code identifiers (classes, methods, variables, packages) and inline code comments
MUST be written in English. All project documentation intended for humans (README,
specs, ADRs, user-facing docs) MUST be written in Portuguese.
**Rationale**: English keeps the code consistent with the broader Kotlin/Spring
ecosystem and third-party libraries; Portuguese documentation matches the team's
working language for communication and onboarding.

### V. Technology Stack Consistency
The project MUST target Kotlin on the JVM (Java 21 toolchain), Spring Boot 4, and
Gradle (Kotlin DSL) as the build tool. Introducing an alternate language runtime,
web framework, or build tool MUST go through a constitution amendment, not a silent
substitution in a feature branch.
**Rationale**: A single, agreed stack avoids fragmentation and keeps tooling (CI,
linters, IDE config) predictable across the project; this reflects the stack already
in use in the repository (Kotlin + Spring Boot 4.1.2).

### VI. Observability
Every feature MUST be observable both locally and in deployed environments:
- **Structured logs**: all logs MUST be emitted in a structured format (JSON key/value),
  never as free-form concatenated strings. Every log entry produced while handling a
  request MUST carry a correlation ID (propagated from the incoming request via W3C
  Trace Context / `traceparent`, or generated at the entry point when absent) together
  with the active `trace_id` and `span_id`. The correlation ID MUST be returned to the
  caller in the response headers.
- **Metrics**: every feature MUST expose metrics relevant to its behavior — at minimum
  request rate, error rate, and latency (RED) for its endpoints, plus the domain-specific
  counters/gauges defined in the feature's plan.
- **Tracing**: every inbound request and every outbound call (database, HTTP, messaging)
  MUST be part of a distributed trace, with meaningfully named spans annotated with
  relevant attributes. Sensitive data (credentials, tokens, personal data) MUST NOT be
  written to logs, metric labels, or span attributes.
**Rationale**: Without correlated logs, metrics, and traces, failures cannot be diagnosed
quickly; making observability a requirement of every feature prevents it from becoming
an afterthought added only after incidents.

### VII. Resilience for External Dependencies
Every call to an external dependency — database, HTTP/gRPC services, message brokers,
caches, third-party APIs — MUST have an explicit, configured timeout (for the database:
connection acquisition and query/statement timeouts; for services: connect and read
timeouts). Relying on library, driver, or OS defaults (including infinite timeouts) is
NOT allowed. Each dependency's failure behavior MUST be specified in the feature's plan —
e.g., fail fast with a defined error response, bounded retry with backoff (only for
idempotent operations), fallback value, or circuit breaking — and MUST be covered by tests
that simulate the failure (timeout, unavailability, error response). Failures MUST be
surfaced through Principle VI signals (error log with correlation ID, error metric, span
status set to error).
**Rationale**: Unbounded waits and unspecified failure modes turn a single slow or failing
dependency into cascading outages; declaring timeouts and failure behavior up front makes
degradation predictable and testable.

## Technical Constraints

Builds MUST use the Gradle wrapper (`./gradlew`) committed in the repository rather
than a locally installed Gradle version, to guarantee reproducible builds. The
package structure MUST mirror the layered architecture (e.g., `controller`,
`service`, `repository` packages per feature or per module). Dependency versions
MUST be managed centrally in `build.gradle.kts` rather than pinned ad hoc per module.

Telemetry (logs, metrics, traces) MUST be implemented with OpenTelemetry — its APIs/SDKs
or the Spring Boot OpenTelemetry integration — and exported via OTLP. Vendor-specific
telemetry SDKs MUST NOT be used directly in application code. For local development,
telemetry MUST be viewable in Grafana, started from a configuration committed in the
repository; the all-in-one `grafana/otel-lgtm` image is the recommended setup. Timeout
values and other resilience parameters MUST be externalized in application configuration
(e.g., `application.yml`) rather than hard-coded.

## Development Workflow

Every change MUST follow this sequence: `/speckit-specify` → `/speckit-clarify`
(if needed) → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`. Each task
in `tasks.md` MUST have its JUnit 5 test(s) written first per Principle II. Pull
requests MUST reference the spec/task they implement. A change MUST NOT be merged
if it skips the spec/plan/tasks pipeline or lacks tests for the behavior it adds. Every plan MUST
describe the feature's logs, metrics, and spans (Principle VI) and list each external
dependency with its timeout and failure behavior (Principle VII); reviewers MUST reject
changes that lack them.

## Governance

This constitution supersedes any conflicting team practice or prior informal
convention. Amendments require: (1) a documented rationale for the change, (2) an
explicit version bump following semantic versioning (MAJOR for incompatible
principle removal/redefinition, MINOR for new/expanded principles or sections,
PATCH for clarifications/wording), and (3) an updated `Last Amended` date. All specs,
plans, and pull requests MUST be checked for compliance with these principles during
review; any deviation MUST be justified in writing (e.g., in the plan's Complexity
Tracking section) or rejected.

**Version**: 2.1.0 | **Ratified**: 2026-11-13 | **Last Amended**: 2026-11-16