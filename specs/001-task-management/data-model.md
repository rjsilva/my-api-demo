# Modelo de Dados: Gerenciamento de Tarefas

**Spec**: [spec.md](./spec.md) | **Pesquisa**: [research.md](./research.md)

## Entidade: Task (Tarefa)

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `id` | `Long` | Gerado pelo sistema | Único, sequencial, atribuído na criação; imutável |
| `title` | `String` | Sim | Não pode ser vazio/branco (FR-002); máximo de 100 caracteres (FR-003) |
| `description` | `String?` | Não | Sem limite de caracteres definido (ver Premissas da spec) |
| `completed` | `Boolean` | Sim | Padrão `false` na criação; `true` após concluir (FR-007); transição é unidirecional nesta versão |
| `createdAt` | `Instant` | Gerado pelo sistema | Definido na criação; imutável; usado para ordenação (FR-006) |

### Regras de validação (na criação)

- `title`: obrigatório, não pode ser vazio ou conter apenas espaços em branco, máximo 100
  caracteres (limite inclusive — 100 é válido, 101 é inválido).
- `description`: opcional, qualquer valor textual é aceito.

### Transições de estado

```
[criada] --> pendente (completed = false)
pendente --> concluída (completed = true)   [FR-007]
concluída --> concluída (completed = true)  [FR-010: idempotente, não é erro]
qualquer estado --> excluída (removida permanentemente) [FR-008]
```

Não existe transição de `concluída` para `pendente` nesta versão (fora de escopo, conforme
Premissas da spec).

### Regra de ordenação (listagem)

A listagem (FR-004, FR-005, FR-006) ordena as tarefas por:

1. `completed` ascendente (pendentes `false` antes de concluídas `true`)
2. `createdAt` ascendente, dentro de cada grupo (mais antiga primeiro)

### Relacionamentos

Nenhum — `Task` é uma entidade independente; não há relação com outras entidades nesta versão
(sistema mono-usuário, sem entidade `Usuário`, conforme Clarifications da spec).