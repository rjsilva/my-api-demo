---
description: "Task list for Gerenciamento de Tarefas"
---

# Tasks: Gerenciamento de Tarefas

**Input**: Design documents from `/specs/001-task-management/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/tasks-api.md, quickstart.md

**Tests**: Incluídas e obrigatórias — a constituição do projeto (Princípio II, NON-NEGOTIABLE)
exige TDD: cada teste JUnit 5 deve ser escrito e falhar antes da implementação correspondente.

**Organization**: Tarefas agrupadas por história de usuário (US1–US4, conforme spec.md), para
permitir implementação e teste independentes de cada uma.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode ser executada em paralelo (arquivos diferentes, sem dependência de tarefas
  incompletas)
- **[Story]**: A qual história de usuário a tarefa pertence (US1, US2, US3, US4)
- Caminhos de arquivo exatos estão incluídos em cada descrição

## Path Conventions

Projeto único (conforme `plan.md`): `src/main/kotlin/com/spec/demo_ia_spec/` e
`src/test/kotlin/com/spec/demo_ia_spec/`.

---

## Phase 1: Setup

**Purpose**: Inicialização do projeto para suportar a API REST

- [ ] T001 Criar os pacotes `controller`, `service`, `repository`, `model`, `dto` e
  `exception` em `src/main/kotlin/com/spec/demo_ia_spec/`, e os pacotes espelhados
  `controller`, `service`, `repository` em `src/test/kotlin/com/spec/demo_ia_spec/`, conforme
  a estrutura definida em `plan.md`.
- [ ] T002 [P] Adicionar as dependências `org.springframework.boot:spring-boot-starter-web` e
  `org.springframework.boot:spring-boot-starter-validation` ao `build.gradle.kts`
  (research.md §1, §2).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestrutura compartilhada por todas as histórias de usuário

**⚠️ CRITICAL**: Nenhuma história de usuário pode ser implementada antes desta fase estar completa

- [ ] T003 [P] Criar a entidade `Task` em
  `src/main/kotlin/com/spec/demo_ia_spec/model/Task.kt` com os campos: `id: Long`,
  `title: String` (não pode ser vazio/em branco — FR-002; máximo de 100 caracteres, limite
  inclusive — FR-003), `description: String?` (opcional, sem limite de caracteres),
  `completed: Boolean` (padrão `false`), `createdAt: Instant` (definido na criação, imutável),
  conforme `data-model.md`.
- [ ] T004 [P] Criar `TaskNotFoundException` em
  `src/main/kotlin/com/spec/demo_ia_spec/exception/TaskNotFoundException.kt`.
- [ ] T005 Criar a interface `TaskRepository` em
  `src/main/kotlin/com/spec/demo_ia_spec/repository/TaskRepository.kt` com os métodos
  `save(task: Task): Task`, `findAllOrdered(): List<Task>` (pendentes antes de concluídas;
  dentro de cada grupo, da mais antiga para a mais recente — FR-005, FR-006), `findById(id:
  Long): Task?`, `deleteById(id: Long): Boolean` (depende de T003).
- [ ] T005a [P] Escrever teste unitário de `InMemoryTaskRepository` em
  `src/test/kotlin/com/spec/demo_ia_spec/repository/InMemoryTaskRepositoryTest.kt` cobrindo:
  `save` gera `id` sequencial; `findAllOrdered()` retorna pendentes antes de concluídas, cada
  grupo ordenado por `createdAt` ascendente (FR-005, FR-006); `findById` retorna `null` para
  `id` inexistente; `deleteById` remove e retorna `true`/`false` conforme existência da
  tarefa (depende de T005; este teste deve falhar antes de T006 ser implementada).
- [ ] T006 Implementar `InMemoryTaskRepository` em
  `src/main/kotlin/com/spec/demo_ia_spec/repository/InMemoryTaskRepository.kt`: armazenamento
  em memória thread-safe, geração de `id` sequencial (`Long`), e ordenação de
  `findAllOrdered()` por `(completed ascendente, createdAt ascendente)` conforme
  `data-model.md` (depende de T005, T005a).
- [ ] T006a [P] Escrever teste unitário de `GlobalExceptionHandler` em
  `src/test/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandlerTest.kt` cobrindo:
  `TaskNotFoundException` mapeada para `404 Not Found` com corpo `{ "message": "..." }`; erro
  de validação (`MethodArgumentNotValidException`) mapeado para `400 Bad Request` com corpo
  `{ "message": "..." }` (FR-009) (depende de T004; este teste deve falhar antes de T007 ser
  implementada).
- [ ] T007 Criar `GlobalExceptionHandler` (`@RestControllerAdvice`) em
  `src/main/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandler.kt` mapeando
  `TaskNotFoundException` → `404 Not Found` e erros de validação (`MethodArgumentNotValidException`)
  → `400 Bad Request`, ambos com corpo `{ "message": "..." }` (FR-009) (depende de T004, T006a).

**Checkpoint**: Fundação pronta — a implementação das histórias de usuário pode começar.

---

## Phase 3: User Story 1 - Criar Tarefa (Priority: P1) 🎯 MVP

**Goal**: Usuário cria uma tarefa informando título obrigatório (até 100 caracteres) e
descrição opcional.

**Independent Test**: Enviar `POST /api/tasks` com título válido (com e sem descrição) e
verificar que retorna `201` com a tarefa pendente; enviar título vazio/em branco ou com mais
de 100 caracteres e verificar que retorna `400`.

### Tests for User Story 1 ⚠️

> **Escrever estes testes PRIMEIRO; garantir que falham antes de implementar.**

- [ ] T008 [P] [US1] Escrever teste unitário de `TaskService.createTask` em
  `src/test/kotlin/com/spec/demo_ia_spec/service/TaskServiceTest.kt` cobrindo: título válido
  cria tarefa pendente (FR-001); título vazio ou apenas espaços é rejeitado (FR-002); título
  com mais de 100 caracteres é rejeitado (FR-003); título com exatamente 100 caracteres é
  aceito (caso de borda).
- [ ] T009 [P] [US1] Escrever teste de contrato para `POST /api/tasks` em
  `src/test/kotlin/com/spec/demo_ia_spec/controller/TaskControllerTest.kt` (`@WebMvcTest` +
  `MockMvc`) cobrindo `201 Created` com corpo válido e `400 Bad Request` com título
  inválido, conforme `contracts/tasks-api.md`.

### Implementation for User Story 1

- [ ] T010 [P] [US1] Criar `CreateTaskRequest` em
  `src/main/kotlin/com/spec/demo_ia_spec/dto/CreateTaskRequest.kt` com `title: String`
  anotado `@NotBlank` e `@Size(max = 100)` (FR-002, FR-003), e `description: String?` opcional.
- [ ] T011 [P] [US1] Criar `TaskResponse` em
  `src/main/kotlin/com/spec/demo_ia_spec/dto/TaskResponse.kt` com `id`, `title`,
  `description`, `completed`, `createdAt`, espelhando `Task`.
- [ ] T012 [US1] Implementar `TaskService.createTask` em
  `src/main/kotlin/com/spec/demo_ia_spec/service/TaskService.kt`, delegando a persistência a
  `TaskRepository.save` e retornando a `Task` criada com `completed = false` (depende de T006,
  T010).
- [ ] T013 [US1] Implementar `POST /api/tasks` em
  `src/main/kotlin/com/spec/demo_ia_spec/controller/TaskController.kt`, recebendo
  `@Valid @RequestBody CreateTaskRequest`, chamando `TaskService.createTask` e retornando
  `201 Created` com `TaskResponse` (depende de T010, T011, T012).

**Checkpoint**: User Story 1 totalmente funcional e testável de forma independente.

---

## Phase 4: User Story 2 - Listar Tarefas (Priority: P1)

**Goal**: Usuário lista todas as tarefas, com as pendentes aparecendo antes das concluídas, e
cada grupo ordenado da mais antiga para a mais recente.

**Independent Test**: Criar algumas tarefas (via `TaskRepository`/`TaskService` diretamente no
teste), marcar parte delas como concluídas, e verificar que `GET /api/tasks` retorna todas as
pendentes antes de todas as concluídas, respeitando a ordem de criação em cada grupo.

### Tests for User Story 2 ⚠️

- [ ] T014 [P] [US2] Escrever teste unitário de `TaskService.listTasks` em
  `TaskServiceTest.kt` cobrindo: lista vazia quando não há tarefas; pendentes antes de
  concluídas (FR-005); ordem de criação preservada dentro de cada grupo (FR-006).
- [ ] T015 [P] [US2] Escrever teste de contrato para `GET /api/tasks` em
  `TaskControllerTest.kt` verificando `200 OK` com array ordenado e array vazio quando não há
  tarefas, conforme `contracts/tasks-api.md`.

### Implementation for User Story 2

- [ ] T016 [US2] Implementar `TaskService.listTasks` em `TaskService.kt`, delegando a
  `TaskRepository.findAllOrdered()` (depende de T006).
- [ ] T017 [US2] Implementar `GET /api/tasks` em `TaskController.kt`, retornando `200 OK` com
  a lista de `TaskResponse` (depende de T011, T016).

**Checkpoint**: User Stories 1 e 2 funcionam de forma independente.

---

## Phase 5: User Story 3 - Marcar Tarefa como Concluída (Priority: P2)

**Goal**: Usuário marca uma tarefa existente como concluída; repetir a operação é idempotente;
tarefa inexistente retorna erro claro.

**Independent Test**: Criar uma tarefa, chamar `PATCH /api/tasks/{id}/complete` e verificar
`200 OK` com `completed: true`; repetir a chamada e verificar que ainda retorna `200 OK`
(FR-010); chamar com um `id` inexistente e verificar `404 Not Found`.

### Tests for User Story 3 ⚠️

- [ ] T018 [P] [US3] Escrever teste unitário de `TaskService.completeTask` em
  `TaskServiceTest.kt` cobrindo: tarefa pendente passa a concluída (FR-007); tarefa já
  concluída permanece concluída sem erro (FR-010); `id` inexistente lança
  `TaskNotFoundException` (FR-009).
- [ ] T019 [P] [US3] Escrever teste de contrato para `PATCH /api/tasks/{id}/complete` em
  `TaskControllerTest.kt` verificando `200 OK` (incluindo chamada repetida) e `404 Not Found`
  para `id` inexistente, conforme `contracts/tasks-api.md`.

### Implementation for User Story 3

- [ ] T020 [US3] Implementar `TaskService.completeTask` em `TaskService.kt`, buscando a
  tarefa via `TaskRepository.findById`, lançando `TaskNotFoundException` se não encontrada, e
  marcando `completed = true` (depende de T004, T006).
- [ ] T021 [US3] Implementar `PATCH /api/tasks/{id}/complete` em `TaskController.kt`,
  retornando `200 OK` com `TaskResponse` atualizada (depende de T011, T020).

**Checkpoint**: User Stories 1, 2 e 3 funcionam de forma independente.

---

## Phase 6: User Story 4 - Excluir Tarefa (Priority: P3)

**Goal**: Usuário exclui uma tarefa existente (pendente ou concluída); tarefa inexistente
retorna erro claro.

**Independent Test**: Criar uma tarefa, chamar `DELETE /api/tasks/{id}` e verificar
`204 No Content`; confirmar que ela não aparece mais em `GET /api/tasks`; chamar `DELETE` com
`id` inexistente e verificar `404 Not Found`.

### Tests for User Story 4 ⚠️

- [ ] T022 [P] [US4] Escrever teste unitário de `TaskService.deleteTask` em
  `TaskServiceTest.kt` cobrindo: tarefa existente (pendente ou concluída) é removida (FR-008);
  `id` inexistente lança `TaskNotFoundException` (FR-009).
- [ ] T023 [P] [US4] Escrever teste de contrato para `DELETE /api/tasks/{id}` em
  `TaskControllerTest.kt` verificando `204 No Content` e `404 Not Found` para `id`
  inexistente, conforme `contracts/tasks-api.md`.

### Implementation for User Story 4

- [ ] T024 [US4] Implementar `TaskService.deleteTask` em `TaskService.kt`, lançando
  `TaskNotFoundException` se o `id` não existir, e removendo a tarefa via
  `TaskRepository.deleteById` (depende de T004, T006).
- [ ] T025 [US4] Implementar `DELETE /api/tasks/{id}` em `TaskController.kt`, retornando
  `204 No Content` (depende de T024).

**Checkpoint**: Todas as histórias de usuário funcionam de forma independente.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T026 [P] Executar manualmente os 5 cenários de `quickstart.md` contra
  `./gradlew bootRun` e confirmar os resultados esperados.
- [ ] T027 [P] Revisar todos os identificadores de código criados (classes, métodos,
  variáveis) para confirmar que estão em inglês, conforme a Constituição (Princípio IV).
- [ ] T028 [P] Revisar `TaskController`, `TaskService` e `InMemoryTaskRepository` para
  confirmar que não há atalhos entre camadas (ex.: controller acessando o repository
  diretamente), conforme a Constituição (Princípio I).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sem dependências — pode começar imediatamente.
- **Foundational (Phase 2)**: Depende da conclusão do Setup — BLOQUEIA todas as histórias de
  usuário.
- **User Stories (Phase 3–6)**: Todas dependem da conclusão da fase Foundational. Podem
  prosseguir em paralelo ou sequencialmente por prioridade (US1 → US2 → US3 → US4).
- **Polish (Phase 7)**: Depende da conclusão das histórias de usuário desejadas.

### User Story Dependencies

- **US1 (P1)**: Sem dependência de outras histórias.
- **US2 (P1)**: Sem dependência de outras histórias (o teste de ordenação com tarefas
  concluídas manipula o estado diretamente via `TaskService`/`TaskRepository` no teste, sem
  precisar da API de US3).
- **US3 (P2)**: Sem dependência de outras histórias.
- **US4 (P3)**: Sem dependência de outras histórias.

### Within Each User Story

- Testes escritos e falhando ANTES da implementação (TDD, Princípio II da constituição).
- DTOs/models antes de services.
- Services antes de controllers/endpoints.

### Parallel Opportunities

- T002 pode rodar em paralelo a T001.
- T003 e T004 podem rodar em paralelo (arquivos diferentes, sem dependências entre si).
- T005a e T006a podem rodar em paralelo entre si (arquivos de teste diferentes; T005a depende
  apenas de T005, T006a depende apenas de T004).
- Dentro de cada história, as duas tarefas de teste marcadas `[P]` podem rodar em paralelo.
- T010 e T011 podem rodar em paralelo (DTOs diferentes).
- As tarefas de Polish (T026–T028) podem rodar em paralelo entre si.

---

## Parallel Example: User Story 1

```bash
# Testes de User Story 1 em paralelo:
Task: "Escrever teste unitário de TaskService.createTask em TaskServiceTest.kt"
Task: "Escrever teste de contrato para POST /api/tasks em TaskControllerTest.kt"

# DTOs de User Story 1 em paralelo:
Task: "Criar CreateTaskRequest em dto/CreateTaskRequest.kt"
Task: "Criar TaskResponse em dto/TaskResponse.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

1. Completar Phase 1: Setup
2. Completar Phase 2: Foundational (CRÍTICO — bloqueia todas as histórias)
3. Completar Phase 3: User Story 1 (Criar Tarefa)
4. Completar Phase 4: User Story 2 (Listar Tarefas)
5. **PARAR e VALIDAR**: testar criação + listagem de forma independente (já é um MVP útil —
   dá para ver as tarefas criadas)

### Incremental Delivery

1. Setup + Foundational → fundação pronta
2. US1 (Criar) → testar → MVP inicial
3. US2 (Listar) → testar → MVP completo (criar e ver tarefas)
4. US3 (Concluir) → testar → ciclo de vida da tarefa completo
5. US4 (Excluir) → testar → funcionalidade completa conforme a spec

## Notes

- `[P]` = arquivos diferentes, sem dependências pendentes.
- Verificar que cada teste falha antes de implementar o código correspondente (TDD).
- Fazer commit após cada tarefa ou grupo lógico de tarefas.
- Parar em qualquer checkpoint para validar a história independentemente.