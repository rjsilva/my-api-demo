# Modelo de Dados: Persistência Durável de Tarefas

**Spec**: [spec.md](./spec.md) | **Pesquisa**: [research.md](./research.md) | **Modelo anterior**: [001 data-model.md](../001-task-management/data-model.md)

## Entidade de domínio: Task (inalterada)

O modelo de domínio `Task` e suas regras (validação do título, transições de estado,
ordenação) são **os mesmos** da funcionalidade 001 — ver
[001 data-model.md](../001-task-management/data-model.md). Nenhum campo novo é exposto ao
usuário.

Única mudança de comportamento: `createdAt` é gerado com precisão de **microssegundos**
(truncado), para que o valor devolvido na criação seja idêntico ao lido do banco depois
(FR-004; research.md §6).

## Tabela `tasks` (PostgreSQL)

Criada pela migração Flyway `V1__create_tasks_table.sql`.

| Coluna | Tipo | Nulo | Regras | Origem |
|---|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | Não | Chave primária; nunca reutilizada (FR-006) | `Task.id` |
| `title` | `VARCHAR(100)` | Não | `CHECK (btrim(title) <> '')` — reforço da validação da API (FR-002/FR-003) | `Task.title` |
| `description` | `TEXT` | Sim | Sem limite (premissa da 001); UTF-8 | `Task.description` |
| `completed` | `BOOLEAN` | Não | `DEFAULT FALSE` | `Task.completed` |
| `created_at` | `TIMESTAMPTZ` | Não | Precisão de microssegundos | `Task.createdAt` |

**Índice**: `idx_tasks_listing ON tasks (completed, created_at, id)` — atende diretamente a
ordenação da listagem.

As restrições no banco (`VARCHAR(100)`, `CHECK`) são uma segunda linha de defesa; a validação
que gera o `400` continua sendo a da API (Bean Validation), então as mensagens de erro não
mudam (FR-007).

## Entidade JPA: `TaskEntity` (camada de repository)

Classe interna à camada de repository, mapeada para `tasks`. Não sai dessa camada: o adapter
`JpaTaskRepository` converte `TaskEntity` ↔ `Task`.

| Propriedade | Tipo Kotlin | Mapeamento |
|---|---|---|
| `id` | `Long?` | `@Id @GeneratedValue(strategy = IDENTITY)`; `null` antes de inserir |
| `title` | `String` | `@Column(nullable = false, length = 100)` |
| `description` | `String?` | `@Column(columnDefinition = "text")` |
| `completed` | `Boolean` | `@Column(nullable = false)`; `var` (muda ao concluir) |
| `createdAt` | `Instant` | `@Column(name = "created_at", nullable = false, updatable = false)` |

Conversão: `Task.id == 0L` (tarefa nova no domínio) ↔ `TaskEntity.id == null`.

## Regra de ordenação (listagem)

1. `completed` ascendente (pendentes antes das concluídas) — FR-005
2. `created_at` ascendente — FR-006 (001)
3. `id` ascendente — desempate determinístico para `created_at` iguais (caso de borda)

## Ciclo de vida no armazenamento

```
criar     -> INSERT (id gerado pelo banco)                     [FR-001]
concluir  -> UPDATE completed = true (idempotente)              [FR-002, FR-010 (001)]
excluir   -> DELETE WHERE id = ? (retorna 0 → "não encontrada") [FR-003]
```

Cada operação é uma única transação (FR-008).

## Evolução do schema

Toda mudança futura entra como um novo script `V<n>__<descricao>.sql` em
`src/main/resources/db/migration`. Scripts já aplicados **nunca** são editados (o Flyway
valida o checksum na inicialização). FR-013.
