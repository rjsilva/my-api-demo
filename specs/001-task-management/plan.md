# Implementation Plan: Gerenciamento de Tarefas

**Branch**: `001-task-management` | **Date**: 2026-11-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-task-management/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

API REST mono-usuário para gerenciar tarefas: criar (título obrigatório ≤100 caracteres,
descrição opcional), listar (pendentes antes de concluídas, cada grupo em ordem de criação),
marcar como concluída (idempotente) e excluir (definitivo). Abordagem técnica: Kotlin + Spring
Boot 4, arquitetura em camadas (controller/service/repository), armazenamento em memória
(sem banco de dados, pois a spec não exige persistência), validação via Jakarta Bean
Validation, tratamento de erros centralizado via `@RestControllerAdvice`.

## Technical Context

**Language/Version**: Kotlin 2.3.21 sobre JVM, toolchain Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (`spring-boot-starter-web`,
`spring-boot-starter-validation` — a adicionar ao `build.gradle.kts`)

**Storage**: Em memória, encapsulada na camada de repository (sem banco de dados — ver
research.md §3)

**Testing**: JUnit 5 (`kotlin-test-junit5`, `spring-boot-starter-test`), `MockMvc`/
`@WebMvcTest` para contrato do controller

**Target Platform**: Servidor JVM (Spring Boot embedded Tomcat), independente de SO

**Project Type**: Serviço web (API REST), projeto único

**Performance Goals**: Não há meta numérica explícita na spec; escopo é uma API de
demonstração mono-usuário com baixo volume — sem exigência de throughput específico

**Constraints**: Mono-usuário, sem autenticação/autorização (FR-011); sem paginação; sem
limite de caracteres na descrição (ver Premissas da spec)

**Scale/Scope**: Baixo volume (uso único/demonstração); 4 endpoints REST; 1 entidade de
domínio (`Task`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Layered Architecture | PASS — controller (HTTP), service (regras de negócio: validação de domínio, ordenação, idempotência), repository (armazenamento em memória) claramente separados |
| II. Test-First Development | PASS — tasks.md (próxima fase) exigirá testes JUnit 5 antes da implementação de cada camada |
| III. Spec-Driven Development | PASS — spec → clarify → plan seguidos nesta ordem antes de qualquer código |
| IV. English Code, Portuguese Documentation | PASS — identificadores de código serão em inglês (`Task`, `TaskService`, etc.); artefatos de documentação (este plano, spec, etc.) em português |
| V. Technology Stack Consistency | PASS — Kotlin + Spring Boot 4 + Gradle, conforme constituição v2.0.0 (atualizada nesta sessão para refletir a stack real do repositório) |

Nenhuma violação identificada. Seção de Complexity Tracking não é necessária.

## Project Structure

### Documentation (this feature)

```text
specs/001-task-management/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões técnicas
├── data-model.md        # Fase 1: entidade Task
├── quickstart.md         # Fase 1: guia de validação manual
├── contracts/
│   └── tasks-api.md     # Fase 1: contrato REST
└── tasks.md             # Fase 2 (gerado por /speckit-tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/com/spec/demo_ia_spec/
├── DemoIaSpecApplication.kt        # já existente
├── controller/
│   └── TaskController.kt           # endpoints REST (/api/tasks)
├── service/
│   └── TaskService.kt              # regras de negócio, validação de domínio, ordenação
├── repository/
│   ├── TaskRepository.kt           # interface
│   └── InMemoryTaskRepository.kt   # implementação em memória
├── model/
│   └── Task.kt                     # entidade de domínio
├── dto/
│   ├── CreateTaskRequest.kt        # request de criação (title, description)
│   └── TaskResponse.kt             # representação de saída
└── exception/
    ├── TaskNotFoundException.kt
    └── GlobalExceptionHandler.kt   # @RestControllerAdvice

src/test/kotlin/com/spec/demo_ia_spec/
├── controller/
│   └── TaskControllerTest.kt       # @WebMvcTest + MockMvc (contrato)
├── service/
│   └── TaskServiceTest.kt          # unit (regras de negócio, ordenação, idempotência)
└── repository/
    └── InMemoryTaskRepositoryTest.kt # unit
```

**Structure Decision**: Projeto único (Option 1 — single project), já que é uma API Spring
Boot sem frontend separado. A estrutura de pacotes espelha a Arquitetura em Camadas exigida
pela constituição (`controller`, `service`, `repository`), com `model`, `dto` e `exception`
como pacotes de suporte dentro do mesmo módulo.

## Complexity Tracking

*Não aplicável — nenhuma violação do Constitution Check a justificar.*