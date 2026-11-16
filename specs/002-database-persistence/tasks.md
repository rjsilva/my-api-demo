---
description: "Task list for Persistência Durável de Tarefas"
---

# Tasks: Persistência Durável de Tarefas

**Input**: Design documents from `/specs/002-database-persistence/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/tasks-api.md,
contracts/health-api.md, quickstart.md

**Tests**: Incluídas e obrigatórias. A constituição (Princípio II, NON-NEGOTIABLE) exige TDD:
cada teste JUnit 5 deve ser escrito e **falhar** antes da implementação correspondente. A
exceção são as tarefas de configuração de infraestrutura sem lógica (`compose.yaml`, README),
validadas manualmente pelo quickstart.

**Organization**: Tarefas agrupadas por história de usuário (US1–US3 da spec.md).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: História de usuário da tarefa (US1, US2, US3)

## Path Conventions

Projeto único: `src/main/kotlin/com/spec/demo_ia_spec/` (abreviado `main/` abaixo quando
óbvio), `src/test/kotlin/com/spec/demo_ia_spec/`, `src/main/resources/`. Testes de
integração precisam de **Docker em execução** (Testcontainers).

---

## Phase 1: Setup

**Purpose**: Dependências e configuração base para persistência em PostgreSQL

- [X] T001 Atualizar `build.gradle.kts`:
  - **Plugins:** adicionar `kotlin("plugin.jpa") version "2.3.21"` ao bloco `plugins` e um bloco `allOpen { annotation("jakarta.persistence.Entity"); annotation("jakarta.persistence.MappedSuperclass"); annotation("jakarta.persistence.Embeddable") }`.
  - **Dependências `implementation`:** `org.springframework.boot:spring-boot-starter-data-jpa`, `org.springframework.boot:spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql` e `org.springframework.boot:spring-boot-starter-actuator`.
  - **Dependência `runtimeOnly`:** `org.postgresql:postgresql`.
  - **Dependências `testImplementation`:** `org.springframework.boot:spring-boot-testcontainers`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql` e o módulo de teste do Data JPA do Boot 4 (`org.springframework.boot:spring-boot-data-jpa-test`).
  - **Versões:** todas sem versão explícita, vindas do BOM do Boot.
  - **Verificação:** rodar `./gradlew compileKotlin compileTestKotlin`. Se algum coordenado não resolver no Boot 4.1.2, ajustar e registrar o nome correto em research.md §3/§4/§10.
  - O `spring-boot-docker-compose` **não** entra aqui (ver T023).
- [X] T002 Atualizar `src/main/resources/application.yaml` (mantendo `spring.application.name`):
  - `spring.datasource.url: ${DB_URL:jdbc:postgresql://localhost:5432/tasks}`
  - `spring.datasource.username: ${DB_USERNAME:tasks}`
  - `spring.datasource.password: ${DB_PASSWORD:tasks}`
  - `spring.jpa.hibernate.ddl-auto: validate`
  - `spring.jpa.open-in-view: false`

  Os timeouts do Hikari (T020) e a configuração do Actuator (T028) entram em tarefas
  próprias (research.md §3).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestrutura de testes com Postgres real; bloqueia todas as histórias

**⚠️ CRITICAL**: Nenhuma história pode começar antes desta fase estar completa

> T003 foi movida para a US1 como **T009a** (migração V1), para que os testes que exercitam a
> tabela (T006, T009) sejam escritos antes dela (Princípio II; achado C1 do `/speckit-analyze`).

- [X] T004 [P] Criar `src/test/kotlin/com/spec/demo_ia_spec/TestcontainersConfiguration.kt`:
  - classe `@TestConfiguration(proxyBeanMethods = false)` com um `@Bean @ServiceConnection` que retorna `PostgreSQLContainer` com a imagem `postgres:18`, a mesma do `compose.yaml` (FR-016)
  - no Testcontainers 2.x, o pacote é `org.testcontainers.postgresql` (research.md §10)
  - expor também a constante `const val POSTGRES_IMAGE = "postgres:18"` num `companion object`, para reuso em T009
- [X] T005 Atualizar `src/test/kotlin/com/spec/demo_ia_spec/DemoIaSpecApplicationTests.kt` com `@Import(TestcontainersConfiguration::class)`. Rodar `./gradlew test --tests '*DemoIaSpecApplicationTests'`:
  - o contexto deve subir contra o Postgres do container
  - ainda não há migrações: o Flyway apenas avisa que não encontrou scripts
  - o `ddl-auto=validate` não deve reclamar (ainda não há entidades)

  Depende de T001, T002 e T004. Os demais testes continuam passando, porque `InMemoryTaskRepository` ainda é o único `TaskRepository`.

**Checkpoint**: aplicação sobe contra Postgres real nos testes.

---

## Phase 3: User Story 1 - Tarefas Sobrevivem ao Reinício da Aplicação (Priority: P1) 🎯 MVP

**Goal**: Tarefas criadas, concluídas e excluídas mantêm exatamente o mesmo estado após reiniciar a aplicação (FR-001 a FR-006, FR-008, FR-013, FR-015).

**Independent Test**: `PersistenceAcrossRestartTest` (T009). Cria, conclui e exclui tarefas, fecha o contexto, sobe outro contexto contra o mesmo banco e verifica a listagem idêntica (SC-001, SC-004).

### Tests for User Story 1 ⚠️ (escrever primeiro; devem FALHAR)

- [X] T006 [P] [US1] Criar `src/test/kotlin/com/spec/demo_ia_spec/repository/JpaTaskRepositoryTest.kt`.

  **Configuração do teste:** `@DataJpaTest` (pacote do Boot 4: `org.springframework.boot.data.jpa.test.autoconfigure`), `@AutoConfigureTestDatabase(replace = NONE)`, `@Import(TestcontainersConfiguration::class, JpaTaskRepository::class)`, com injeção de `TaskRepository` e de `TestEntityManager`.

  **Leitura do banco, não do cache:** o `@DataJpaTest` roda numa transação com contexto de persistência. Em **todo** caso que escreve e depois lê (a, b, c, e, f, g, h), chamar `testEntityManager.flush()` e `testEntityManager.clear()` entre a escrita e a leitura, para que a leitura venha do Postgres. Sem isso, (a) e (h) passam sem provar o round-trip, e (e) pode devolver a tarefa já excluída.

  **Casos:**
  - (a) `save` de `Task(id = 0, ...)` devolve `id > 0` e `findById` devolve título, descrição, `completed` e `createdAt` iguais (com `createdAt` truncado em µs);
  - (b) `findAllOrdered()` devolve as pendentes antes das concluídas, cada grupo por `createdAt` ascendente;
  - (c) duas tarefas com o **mesmo** `createdAt` saem ordenadas por `id` ascendente;
  - (d) `findById` de um id inexistente devolve `null`;
  - (e) `deleteById` devolve `true` para um id existente (a tarefa some) e `false` para um inexistente;
  - (f) depois de excluir a tarefa de maior id, a próxima `save` recebe um id **maior** que o excluído (FR-006);
  - (g) `save` de uma tarefa existente com `completed = true` persiste a mudança (FR-002);
  - (h) uma descrição com 10.000 caracteres e acentos/emoji (`"Ação ✅ ünïcødé"`) volta idêntica.

  Não compila/falha até T009a–T012.
- [X] T007 [P] [US1] Reescrever `src/test/kotlin/com/spec/demo_ia_spec/service/TaskServiceTest.kt` sem usar `InMemoryTaskRepository` (que será removido):
  - **Mock:** usar `Mockito.mock(TaskRepository::class.java)`. Evitar `any()` em parâmetros Kotlin não anuláveis; preferir `ArgumentCaptor` ou stubs com valores concretos (research.md §10).
  - **Casos existentes, adaptados ao mock:** criar tarefa pendente; título com 100 caracteres aceito; listar delega a `findAllOrdered`; concluir uma tarefa pendente salva com `completed = true`; concluir uma já concluída não chama `save` (idempotente); concluir inexistente lança `TaskNotFoundException`.
  - **Novo:** `createTask` gera `createdAt` com `nano % 1000 == 0`, ou seja, truncado em µs (research.md §6).
  - **Novo:** `deleteTask` chama `deleteById` uma única vez e **não** chama `findById`.
  - **Novo:** `deleteTask` lança `TaskNotFoundException` quando `deleteById` devolve `false`.

  Os novos casos falham até T014.
- [X] T008 [P] [US1] Remover `src/test/kotlin/com/spec/demo_ia_spec/repository/InMemoryTaskRepositoryTest.kt` (FR-015). A cobertura passa para T006.
- [X] T009 [P] [US1] Criar `src/test/kotlin/com/spec/demo_ia_spec/integration/PersistenceAcrossRestartTest.kt`.

  **Container:** um `PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE)` próprio, iniciado em `@BeforeAll` e parado em `@AfterAll`. Não usar `@SpringBootTest`.

  **Subida da aplicação:** cada "execução" é um `SpringApplicationBuilder(DemoIaSpecApplication::class.java).properties(...)` com:
  - `spring.datasource.url`, `username` e `password` do container
  - `server.port=0`
  - `spring.docker.compose.enabled=false`

  A porta vem de `local.server.port` e as chamadas HTTP usam `RestClient`.

  **Fluxo:**
  1. Execução 1: criar "A", "B" e "C"; concluir "A"; excluir "B"; guardar o JSON de `GET /api/tasks` e o id de "B"; fechar o contexto.
  2. Execução 2, contra o mesmo container:
     - `GET /api/tasks` é igual ao guardado (ids, `createdAt`, `completed` e a ordem "C", "A") — SC-001.
     - `PATCH /api/tasks/{idB}/complete` e `DELETE /api/tasks/{idB}` → `404`.
     - um `POST` novo recebe um id maior que todos os anteriores, inclusive o de "B".

  Falha até T009a–T014.

### Implementation for User Story 1

- [X] T009a [US1] Criar a migração Flyway `src/main/resources/db/migration/V1__create_tasks_table.sql`, criando a tabela `tasks` conforme data-model.md:
  - `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY` ("nunca reutilizada (FR-006)")
  - `title VARCHAR(100) NOT NULL` com `CHECK (btrim(title) <> '')`
  - `description TEXT NULL` ("Sem limite")
  - `completed BOOLEAN NOT NULL DEFAULT FALSE`
  - `created_at TIMESTAMPTZ NOT NULL` ("Precisão de microssegundos")
  - o índice `CREATE INDEX idx_tasks_listing ON tasks (completed, created_at, id);`

  Depende de T006–T009 já escritos e falhando. Junto com T010–T014, faz T006 e T009 passarem. Após o merge, este script **nunca** deve ser editado (FR-013).

- [X] T010 [P] [US1] Criar `src/main/kotlin/com/spec/demo_ia_spec/repository/TaskEntity.kt`: `@Entity @Table(name = "tasks")` como classe (não `data class`), com as propriedades abaixo, conforme data-model.md:
  - `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null`
  - `@Column(nullable = false, length = 100) var title: String`
  - `@Column(columnDefinition = "text") var description: String?`
  - `@Column(nullable = false) var completed: Boolean`
  - `@Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant`

  Incluir também:
  - `fun toDomain(): Task`, que faz `id!!` → `Task.id`
  - `companion object { fun fromDomain(task: Task): TaskEntity }`, que mapeia `Task.id == 0L` → `id = null`
- [X] T011 [US1] Criar `src/main/kotlin/com/spec/demo_ia_spec/repository/TaskJpaRepository.kt`: `interface TaskJpaRepository : JpaRepository<TaskEntity, Long>`, com:
  - `fun findAllByOrderByCompletedAscCreatedAtAscIdAsc(): List<TaskEntity>`
  - `@Modifying(clearAutomatically = true, flushAutomatically = true) @Query("delete from TaskEntity t where t.id = :id") fun deleteTaskById(@Param("id") id: Long): Int` (research.md §7). As duas opções evitam que o cache do JPA fique com a entidade excluída ou com escritas pendentes.

  Depende de T010.
- [X] T012 [US1] Criar `src/main/kotlin/com/spec/demo_ia_spec/repository/JpaTaskRepository.kt`: classe `@Repository @Transactional` que implementa `TaskRepository` e recebe `TaskJpaRepository`.
  - `save` = `jpa.save(TaskEntity.fromDomain(task)).toDomain()`.
  - `findAllOrdered` (`@Transactional(readOnly = true)`) usa `findAllByOrderByCompletedAscCreatedAtAscIdAsc().map { it.toDomain() }`.
  - `findById` (`readOnly = true`) = `jpa.findById(id).orElse(null)?.toDomain()`.
  - `deleteById` = `jpa.deleteTaskById(id) > 0`.

  Depende de T011.
- [X] T013 [US1] Remover `src/main/kotlin/com/spec/demo_ia_spec/repository/InMemoryTaskRepository.kt` (FR-015). Garantir que nenhuma referência a ele sobra em `src/` (`grep -r InMemoryTaskRepository src`). Fazer junto com T012, para não existirem dois beans `TaskRepository`.
- [X] T014 [US1] Atualizar `src/main/kotlin/com/spec/demo_ia_spec/service/TaskService.kt`:
  - `createTask` passa a usar `Instant.now().truncatedTo(ChronoUnit.MICROS)` (research.md §6)
  - `deleteTask` passa a ser `if (!taskRepository.deleteById(id)) throw TaskNotFoundException(id)`, sem `findById` prévio (research.md §7)
  - `listTasks` e `completeTask` ficam inalterados
- [X] T015 [US1] Rodar `./gradlew test`. T006, T007 e T009 devem passar, e também `TaskControllerTest`, `GlobalExceptionHandlerTest` e `DemoIaSpecApplicationTests`.

**Checkpoint**: tarefas persistem entre reinícios; MVP entregue.

---

## Phase 4: User Story 2 - Operações Existentes Continuam Iguais (Priority: P1)

**Goal**: Criar, listar, concluir e excluir mantêm contrato, validações e mensagens da 001 (FR-007), com falhas de armazenamento tratadas de forma clara (FR-009, FR-010).

**Independent Test**: `TaskApiIntegrationTest` (T016) executa todos os cenários de aceitação da 001 via HTTP contra Postgres real (SC-002). Os casos de 503 e de inicialização sem banco têm testes dedicados (T017–T020).

### Tests for User Story 2 ⚠️ (escrever primeiro)

- [X] T016 [P] [US2] Criar `src/test/kotlin/com/spec/demo_ia_spec/integration/TaskApiIntegrationTest.kt`.

  **Configuração:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` com `@Import(TestcontainersConfiguration::class)` e chamadas via `RestClient`/`TestRestTemplate`. Em `@BeforeEach`, executar `TRUNCATE TABLE tasks` via `JdbcTemplate`, **sem** `RESTART IDENTITY`, para não reutilizar ids.

  Cobrir todos os cenários de aceitação da spec 001 (`specs/001-task-management/spec.md`), conforme `specs/001-task-management/contracts/tasks-api.md`.

  **Criar:**
  - criar sem descrição → `201`, `completed: false` e `description: null`
  - criar com descrição → `201` com os campos informados
  - título vazio ou só com espaços → `400` `{ "message": ... }`
  - título com 101 caracteres → `400`, e a listagem continua vazia (nada salvo)
  - título com exatamente 100 caracteres → `201`

  **Listar:**
  - lista vazia → `200 []`
  - pendentes antes das concluídas, cada grupo em ordem de criação

  **Concluir:**
  - concluir → `200`, e a tarefa vai para o fim da lista
  - concluir de novo → `200` (idempotente)
  - concluir inexistente → `404`

  **Excluir:**
  - excluir → `204`, e a tarefa some da listagem
  - excluir inexistente → `404`

  Deve passar já após US1. Se algum caso falhar, é regressão a corrigir antes de seguir.
- [X] T017 [P] [US2] Atualizar `src/test/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandlerTest.kt`, adicionando dois casos:
  - `CannotCreateTransactionException("x")` → `503 SERVICE_UNAVAILABLE`
  - `DataAccessResourceFailureException("x")` → `503 SERVICE_UNAVAILABLE`

  Em ambos, o `body.message` deve ser **exatamente** `"Storage temporarily unavailable. Please try again later."` e não conter `"x"` (contracts/tasks-api.md). Falha até T021.
- [X] T018 [P] [US2] Atualizar `src/test/kotlin/com/spec/demo_ia_spec/controller/TaskControllerTest.kt`, adicionando três casos:
  - `taskService.listTasks()` lança `CannotCreateTransactionException("db down")` → `GET /api/tasks` responde `503` com `$.message` igual a `"Storage temporarily unavailable. Please try again later."`
  - `taskService.createTask(...)` lança `DataAccessResourceFailureException("db down")` → `POST /api/tasks` responde `503`
  - `taskService.listTasks()` lança `DataIntegrityViolationException("x")` → a resposta **não** é `503` (research.md §8)

  Falha até T021.
- [X] T019 [P] [US2] Criar `src/test/kotlin/com/spec/demo_ia_spec/integration/StartupWithoutDatabaseTest.kt` (FR-010).
  - **Subida da aplicação:** `SpringApplicationBuilder(DemoIaSpecApplication::class.java).properties("spring.datasource.url=jdbc:postgresql://localhost:1/tasks", "server.port=0", "spring.docker.compose.enabled=false")`, onde a porta 1 garante conexão recusada.
  - **Verificação:** `run()` lança exceção, e o tempo decorrido fica entre 25 s e 60 s.
  - **Mensagem clara:** a cadeia de causas da exceção contém uma falha de conexão com o banco (ex.: `com.zaxxer.hikari.pool.HikariPool.PoolInitializationException` ou `java.net.ConnectException`), e alguma mensagem da cadeia menciona a conexão recusada (FR-010).
  - Marcar com `@Tag("slow")`.

  Hoje falha rápido demais (Hikari `initialization-fail-timeout` padrão = 1 ms). Passa após T020.

### Implementation for User Story 2

- [X] T020 [US2] Em `src/main/resources/application.yaml`, adicionar:
  - `spring.datasource.hikari.initialization-fail-timeout: 30000`, para esperar até 30 s pelo banco na inicialização (FR-010, research.md §9)
  - `spring.datasource.hikari.connection-timeout: 5000`, para o `503` sair em ~5 s (research.md §8)

  Faz T019 passar.
- [X] T021 [US2] Em `src/main/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandler.kt`, adicionar `@ExceptionHandler(CannotCreateTransactionException::class, DataAccessResourceFailureException::class) fun handleStorageUnavailable(ex: Exception): ResponseEntity<ErrorResponse>`, que devolve `503` com `ErrorResponse("Storage temporarily unavailable. Please try again later.")`. Registrar a causa com `log.warn` (logger SLF4J), sem expor nada no corpo. **Não** capturar `DataAccessException` genérica. Faz T017 e T018 passarem.
- [X] T022 [US2] Rodar `./gradlew test`. Tudo deve passar, incluindo T016–T019.

**Checkpoint**: contrato da 001 preservado contra Postgres real; falhas de banco → 503; inicialização espera até 30 s.

---

## Phase 5: User Story 3 - Subir a Base de Dados de Desenvolvimento com Um Comando (Priority: P2)

**Goal**: Qualquer desenvolvedor sobe o banco local com um comando, os dados persistem entre paradas e há documentação de subir, parar e apagar (FR-011, FR-012, FR-014).

**Independent Test**: a partir de um clone limpo, `docker compose up -d` deixa o serviço `healthy`, e `./gradlew bootRun` consegue criar e listar tarefas sem outra configuração (quickstart.md §1, §2, §4).

### Implementation for User Story 3

- [X] T023 [US3] Criar `compose.yaml` na raiz, com o serviço `postgres` (research.md §1):
  - **Imagem e ambiente:** `image: postgres:18`, com `environment` `POSTGRES_DB: tasks`, `POSTGRES_USER: tasks` e `POSTGRES_PASSWORD: tasks` (os mesmos padrões do `application.yaml`).
  - **Porta:** `ports: ["5432:5432"]`.
  - **Volume:** `volumes: [tasks-db-data:/var/lib/postgresql]`. No Postgres 18, o volume vai em `/var/lib/postgresql`, **não** em `/var/lib/postgresql/data`.
  - **Healthcheck:** `test: ["CMD-SHELL", "pg_isready -U tasks -d tasks"]`, `interval: 5s`, `timeout: 5s`, `retries: 10`.
  - **Volumes de topo:** `volumes: { tasks-db-data: {} }`.
- [X] T024 [US3] Em `build.gradle.kts`, adicionar `developmentOnly("org.springframework.boot:spring-boot-docker-compose")`, para que `./gradlew bootRun` suba o `compose.yaml` automaticamente (research.md §2). Confirmar que `./gradlew test` continua sem tentar subir o Compose (`skip.in-tests` padrão).
- [X] T025 [P] [US3] Criar `README.md` na raiz, em português (Princípio IV, FR-014), com as seções:
  - **Pré-requisitos:** JDK 21, Docker com Compose v2 e a porta 5432 livre.
  - **Subir o banco:** `docker compose up -d`.
  - **Rodar a aplicação:** `./gradlew bootRun`, que também sobe o banco sozinho.
  - **Parar o banco:** `docker compose stop` ou `docker compose down`, que mantêm os dados.
  - **Apagar os dados locais:** `docker compose down -v`.
  - **Testes:** `./gradlew test`, que exige Docker e não usa o banco de desenvolvimento.
  - **Health:** `GET /actuator/health`.
  - **Endpoints:** link para `specs/001-task-management/contracts/tasks-api.md`.
- [X] T026 [US3] Validar manualmente os passos 1, 2 e 4 de `specs/002-database-persistence/quickstart.md`:
  - o serviço fica `healthy` em < 2 min (SC-003)
  - `bootRun` sobe sem configuração extra
  - os dados sobrevivem a `docker compose down` + `up -d`

**Checkpoint**: ambiente de desenvolvimento reproduzível com um comando.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Observabilidade, desempenho e validação final

- [X] T027 [P] Criar `src/test/kotlin/com/spec/demo_ia_spec/integration/HealthEndpointIntegrationTest.kt` (contracts/health-api.md).
  - **Configuração:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` com `@Import(TestcontainersConfiguration::class)`.
  - **Verificação:** `GET /actuator/health` responde `200`, com `$.status == "UP"`, `$.components.db.status == "UP"` e **sem** a chave `$.components.db.details`.
  - **Exposição:** `GET /actuator/env` responde `404`, porque não está exposto.

  Falha até T028.
- [X] T028 Em `src/main/resources/application.yaml`, adicionar:
  - `management.endpoints.web.exposure.include: health`
  - `management.endpoint.health.show-components: always`, **sem** `show-details` (research.md §11)

  Faz T027 passar.
- [X] T029 [P] Criar `src/test/kotlin/com/spec/demo_ia_spec/integration/ListingPerformanceTest.kt` (SC-005).
  - **Configuração:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` com `@Import(TestcontainersConfiguration::class)`, executando `TRUNCATE TABLE tasks` antes.
  - **Massa de dados:** inserir 1.000 tarefas via `TaskRepository` (metade concluída).
  - **Verificação:** após uma chamada de aquecimento, `GET /api/tasks` devolve 1.000 itens na ordem correta em < 1 s.
  - **Tempo das escritas:** `POST`, `PATCH .../complete` e `DELETE` também levam < 1 s cada com a tabela cheia.
- [X] T030 Rodar `./gradlew test` completo, com Docker ligado e o banco de desenvolvimento **parado** (prova de FR-016). Tudo deve passar.
- [X] T031 Validar manualmente os passos 3, 5, 6 e 8 de `specs/002-database-persistence/quickstart.md`:
  - reinício com a mesma listagem
  - `docker compose stop` → `503` em ~5 s e health `DOWN`; depois `start` → volta sem reiniciar a aplicação
  - jar sem banco → encerra em ~30 s; banco subindo em ~10 s → a aplicação inicia
  - `down -v` → dados apagados
- [X] T032 Revisão final de conformidade:
  - `grep -ri "InMemory" src` não encontra nada (FR-015)
  - nenhum código fora de `repository/` importa `jakarta.persistence` ou `org.springframework.data` (Princípio I)
  - identificadores e mensagens em inglês, documentação em português (Princípio IV)
  - `V1__create_tasks_table.sql` sem alterações pendentes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup e bloqueia todas as histórias.
- **US1 (Phase 3)**: depende da Foundational. É o MVP.
- **US2 (Phase 4)**: depende da Foundational. Na prática, T016 e T022 dependem de US1 (o repository JPA precisa existir para a regressão contra Postgres). T017–T021 podem ser feitas em paralelo com US1.
- **US3 (Phase 5)**: depende só do Setup (o `application.yaml` de T002). Pode ser feita em paralelo com US1/US2, mas a validação manual T026 precisa de US1 pronta.
- **Polish (Phase 6)**: depende de US1–US3.

### Within Each User Story

- Os testes (T006–T009, T016–T019, T027, T029) são escritos antes e **falham** antes da implementação.
- Em US1: T006–T009 (testes, falhando) → T009a (migração) → T010 → T011 → T012 + T013 (juntas) → T014 → T015.
- Em US2: T020 e T021 são independentes entre si; T022 fecha a fase.

### Parallel Opportunities

- **Setup:** T001 e T002 tocam arquivos diferentes, mas T002 só é verificável depois de T001.
- **Foundational:** T004 → T005.
- **Testes de US1:** T006, T007, T008 e T009 em paralelo.
- **Testes de US2:** T016, T017, T018 e T019 em paralelo, e em paralelo com a implementação de US1.
- **US3:** T023 e T025 em paralelo com qualquer fase depois do Setup.
- **Polish:** T027 e T029 em paralelo.

---

## Parallel Example: User Story 1

```bash
# Testes de US1 juntos (arquivos diferentes):
Task: "JpaTaskRepositoryTest em src/test/kotlin/com/spec/demo_ia_spec/repository/JpaTaskRepositoryTest.kt"
Task: "Reescrever TaskServiceTest com mock em src/test/kotlin/com/spec/demo_ia_spec/service/TaskServiceTest.kt"
Task: "Remover src/test/kotlin/com/spec/demo_ia_spec/repository/InMemoryTaskRepositoryTest.kt"
Task: "PersistenceAcrossRestartTest em src/test/kotlin/com/spec/demo_ia_spec/integration/PersistenceAcrossRestartTest.kt"
```

## Parallel Example: User Story 2

```bash
Task: "TaskApiIntegrationTest em src/test/kotlin/com/spec/demo_ia_spec/integration/TaskApiIntegrationTest.kt"
Task: "Casos 503 em src/test/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandlerTest.kt"
Task: "Casos 503 em src/test/kotlin/com/spec/demo_ia_spec/controller/TaskControllerTest.kt"
Task: "StartupWithoutDatabaseTest em src/test/kotlin/com/spec/demo_ia_spec/integration/StartupWithoutDatabaseTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) → Phase 2 (Foundational).
2. Phase 3 (US1) → **PARAR E VALIDAR** com `PersistenceAcrossRestartTest`.
3. A partir daí, as tarefas já sobrevivem a reinícios (rodando com `docker compose up -d`
   manual até US3 existir, ou com um Postgres apontado por `DB_URL`).

### Incremental Delivery

1. Setup + Foundational → o schema e os testes com Postgres funcionam.
2. US1 → persistência entre reinícios (MVP).
3. US2 → regressão completa da 001, 503 e espera na inicialização.
4. US3 → `compose.yaml`, `bootRun` automático e README.
5. Polish → health, desempenho e validação manual final.

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefas incompletas.
- Cada commit deve referenciar o ID da tarefa (Development Workflow da constituição).
- Os testes de integração exigem Docker. `StartupWithoutDatabaseTest` é lento (~30 s) e está
  marcado com `@Tag("slow")`.
- Nunca editar `V1__create_tasks_table.sql` depois de aplicado; qualquer mudança de schema
  entra como `V2__...sql`.
