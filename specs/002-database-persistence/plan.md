# Implementation Plan: Persistência Durável de Tarefas

**Branch**: `feat/002-database-persistence` | **Date**: 2026-11-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-database-persistence/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Trocar o armazenamento em memória por PostgreSQL, sem mudar o comportamento da API de
tarefas. A interface de domínio `TaskRepository` é mantida, e uma nova implementação
`JpaTaskRepository` (Spring Data JPA) substitui a `InMemoryTaskRepository`, que é removida.
O schema é versionado com Flyway. Em desenvolvimento, o banco sobe com um único comando
(`docker compose up -d`, via `compose.yaml` na raiz), ou automaticamente no `./gradlew bootRun`
pelo `spring-boot-docker-compose`. Os testes de integração usam Testcontainers com a mesma
imagem do Postgres. A falta de banco vira `503` no formato de erro existente, a aplicação
espera até 30 s pelo banco ao iniciar, e o Actuator expõe `/actuator/health` com o status do
banco.

## Technical Context

**Language/Version**: Kotlin 2.3.21 sobre JVM, toolchain Java 21

**Primary Dependencies**: Spring Boot 4.1.2. Já existentes: `spring-boot-starter-web` e
`spring-boot-starter-validation`. Novas:
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`
- `spring-boot-starter-actuator`
- `org.postgresql:postgresql` (runtimeOnly)
- `spring-boot-docker-compose` (developmentOnly)
- plugin `kotlin("plugin.jpa")` + `allOpen` para as anotações JPA

**Storage**: PostgreSQL 18 (`postgres:18`), via Docker Compose em desenvolvimento. O schema é
gerido por migrações Flyway (`src/main/resources/db/migration`).

**Testing**: JUnit 5, `@WebMvcTest`/MockMvc e Mockito (unitários), mais
`spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` e
o módulo de teste do Data JPA (integração). Ver research.md §10.

**Target Platform**: Servidor JVM (Tomcat embutido). Desenvolvimento local com Docker.

**Project Type**: Serviço web (API REST), projeto único

**Performance Goals**: SC-005: criar, concluir, excluir e listar até 1.000 tarefas em < 1 s
cada.

**Constraints**:
- API idêntica à da 001 (FR-007).
- `503` com mensagem genérica quando o banco cai (FR-009).
- Espera de no máximo 30 s pelo banco na inicialização (FR-010).
- Sem armazenamento em memória (FR-015).
- Testes sem depender do banco de desenvolvimento (FR-016).
- Mono-usuário, sem autenticação.

**Scale/Scope**: Baixo volume (demonstração, mono-usuário); 1 tabela; 4 endpoints de tarefas
inalterados mais 1 endpoint de health.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação (pré-pesquisa) | Reavaliação (pós-design) |
|---|---|---|
| I. Layered Architecture | PASS: o JPA fica restrito ao pacote `repository`; service e controller não conhecem o banco | PASS: `TaskEntity`, `TaskJpaRepository` e o adapter ficam em `repository`, assim como o `@Transactional`; o service só usa a interface de domínio. O mapeamento de exceções de banco para `503` fica no `GlobalExceptionHandler` (preocupação HTTP), sem acesso ao banco |
| II. Test-First Development | PASS: o tasks.md exigirá testes antes de cada mudança | PASS com exceção justificada (ver Complexity Tracking): estratégia de testes por camada definida (research.md §10), incluindo os testes de regressão da 001 e de reinício; a US3 é validada manualmente |
| III. Spec-Driven Development | PASS: specify → clarify (5 respostas) → plan | PASS |
| IV. English Code, Portuguese Documentation | PASS | PASS: identificadores (`JpaTaskRepository`, `TaskEntity`, `V1__create_tasks_table.sql`) e mensagens da API em inglês; spec, plano e quickstart em português; o README ganha seção em português |
| V. Technology Stack Consistency | PASS: continua Kotlin + Spring Boot 4 + Gradle Kotlin DSL; PostgreSQL e Docker não são linguagem, framework web nem ferramenta de build | PASS: as versões das dependências vêm do BOM do Boot, declarado no `build.gradle.kts` (Restrições Técnicas) |

Uma exceção justificada ao Princípio II, registrada em Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-database-persistence/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões técnicas
├── data-model.md        # Fase 1: tabela tasks, TaskEntity, ordenação
├── quickstart.md        # Fase 1: guia de validação ponta a ponta
├── contracts/
│   ├── tasks-api.md     # Fase 1: alterações no contrato da 001 (503)
│   └── health-api.md    # Fase 1: /actuator/health
├── checklists/
│   └── requirements.md  # Checklist de qualidade da spec
└── tasks.md             # Fase 2 (gerado por /speckit-tasks)
```

### Source Code (repository root)

```text
compose.yaml                                   # NOVO: postgres:18, volume, healthcheck
build.gradle.kts                               # ALTERADO: deps + plugin.jpa + allOpen
README.md                                      # NOVO: como subir/parar/apagar o banco (FR-014)

src/main/resources/
├── application.yaml                           # ALTERADO: datasource, hikari, jpa, actuator
└── db/migration/
    └── V1__create_tasks_table.sql             # NOVO

src/main/kotlin/com/spec/demo_ia_spec/
├── controller/TaskController.kt               # inalterado
├── service/TaskService.kt                     # ALTERADO: createdAt truncado em µs; delete usa retorno booleano
├── repository/
│   ├── TaskRepository.kt                      # inalterado (interface de domínio)
│   ├── InMemoryTaskRepository.kt              # REMOVIDO (FR-015)
│   ├── TaskEntity.kt                          # NOVO: @Entity tasks
│   ├── TaskJpaRepository.kt                   # NOVO: JpaRepository<TaskEntity, Long>
│   └── JpaTaskRepository.kt                   # NOVO: adapter @Repository @Transactional
├── model/Task.kt                              # inalterado
├── dto/                                       # inalterado
└── exception/
    ├── GlobalExceptionHandler.kt              # ALTERADO: handler 503
    └── (demais inalterados)

src/test/kotlin/com/spec/demo_ia_spec/
├── TestcontainersConfiguration.kt             # NOVO: @ServiceConnection postgres:18
├── DemoIaSpecApplicationTests.kt              # ALTERADO: importa TestcontainersConfiguration
├── repository/
│   ├── InMemoryTaskRepositoryTest.kt          # REMOVIDO
│   └── JpaTaskRepositoryTest.kt               # NOVO: @DataJpaTest + Postgres real
├── service/TaskServiceTest.kt                 # ALTERADO: TaskRepository mockado
├── controller/TaskControllerTest.kt           # ALTERADO: caso 503
├── exception/GlobalExceptionHandlerTest.kt    # ALTERADO: caso 503
└── integration/
    ├── TaskApiIntegrationTest.kt              # NOVO: cenários da 001 via HTTP
    ├── PersistenceAcrossRestartTest.kt        # NOVO: dois contextos, mesmo container
    ├── StartupWithoutDatabaseTest.kt          # NOVO: espera de 30 s e falha clara (@Tag("slow"))
    ├── HealthEndpointIntegrationTest.kt       # NOVO: /actuator/health com db
    └── ListingPerformanceTest.kt              # NOVO: 1.000 tarefas em < 1 s (SC-005)
```

**Structure Decision**: Projeto único, mantendo a estrutura em camadas da 001. Todo o código
de persistência novo fica no pacote `repository`. O pacote de teste `integration` agrupa os
testes que sobem a aplicação inteira contra o Postgres do Testcontainers.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Princípio II: US3 (`compose.yaml`, dependência `developmentOnly` do `spring-boot-docker-compose`, `README.md`) sem teste JUnit 5 prévio | São configuração declarativa de ambiente local e documentação, sem lógica de produção; o comportamento (FR-011, FR-012, FR-014, SC-003) é validado manualmente pelos passos 1, 2, 4 e 8 do quickstart.md (tarefas T026, T031) | Um teste automatizado subiria o `compose.yaml` via Docker dentro do JUnit (ex.: `ComposeContainer` do Testcontainers): duplica o que o Testcontainers já cobre (mesma imagem `postgres:18`), deixa a suíte mais lenta e testa o Docker Compose, não a aplicação |
