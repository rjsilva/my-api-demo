<!--
Sync Impact Report
Version change: [template] → 1.0.0 (initial ratification)
Modified principles: none (first version)
Added principles: I. Layered Architecture, II. Test-First Development (NON-NEGOTIABLE),
  III. Spec-Driven Development, IV. English Code, Portuguese Documentation,
  V. Technology Stack Consistency
Added sections: Technical Constraints, Development Workflow
Removed sections: none
Deferred TODOs: none — all placeholders replaced with concrete values
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
**Rationale**: English keeps the code consistent with the broader Java/Spring
ecosystem and third-party libraries; Portuguese documentation matches the team's
working language for communication and onboarding.

### V. Technology Stack Consistency
The project MUST target Java 21, Spring Boot 3, and Gradle as the build tool.
Introducing an alternate language runtime, web framework, or build tool MUST go
through a constitution amendment, not a silent substitution in a feature branch.
**Rationale**: A single, agreed stack avoids fragmentation and keeps tooling (CI,
linters, IDE config) predictable across the project.

## Technical Constraints

Builds MUST use the Gradle wrapper (`./gradlew`) committed in the repository rather
than a locally installed Gradle version, to guarantee reproducible builds. The
package structure MUST mirror the layered architecture (e.g., `controller`,
`service`, `repository` packages per feature or per module). Dependency versions
MUST be managed centrally in `build.gradle.kts` rather than pinned ad hoc per module.

## Development Workflow

Every change MUST follow this sequence: `/speckit-specify` → `/speckit-clarify`
(if needed) → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`. Each task
in `tasks.md` MUST have its JUnit 5 test(s) written first per Principle II. Pull
requests MUST reference the spec/task they implement. A change MUST NOT be merged
if it skips the spec/plan/tasks pipeline or lacks tests for the behavior it adds.

## Governance

This constitution supersedes any conflicting team practice or prior informal
convention. Amendments require: (1) a documented rationale for the change, (2) an
explicit version bump following semantic versioning (MAJOR for incompatible
principle removal/redefinition, MINOR for new/expanded principles or sections,
PATCH for clarifications/wording), and (3) an updated `Last Amended` date. All specs,
plans, and pull requests MUST be checked for compliance with these principles during
review; any deviation MUST be justified in writing (e.g., in the plan's Complexity
Tracking section) or rejected.

**Version**: 1.0.0 | **Ratified**: 2026-11-13 | **Last Amended**: 2026-11-13