# Contrato da API: Tarefas

**Modelo de dados**: [data-model.md](../data-model.md)

Base path: `/api/tasks`

Representação de `Task` retornada pela API:

```json
{
  "id": 1,
  "title": "Comprar leite",
  "description": null,
  "completed": false,
  "createdAt": "2026-11-13T10:00:00Z"
}
```

## POST /api/tasks

Cria uma nova tarefa. (FR-001, FR-002, FR-003)

**Request body**:

```json
{
  "title": "Comprar leite",
  "description": "Leite integral, 2 litros"
}
```

- `title`: obrigatório, não vazio/branco, máximo 100 caracteres.
- `description`: opcional.

**Responses**:

| Status | Quando | Corpo |
|---|---|---|
| `201 Created` | Tarefa criada com sucesso | `Task` criada, `completed: false` |
| `400 Bad Request` | `title` ausente, vazio/branco ou acima de 100 caracteres | `{ "message": "..." }` |

## GET /api/tasks

Lista todas as tarefas. (FR-004, FR-005, FR-006)

**Responses**:

| Status | Quando | Corpo |
|---|---|---|
| `200 OK` | Sempre | Array de `Task`, pendentes antes de concluídas, cada grupo ordenado da mais antiga para a mais recente. Array vazio se não houver tarefas. |

## PATCH /api/tasks/{id}/complete

Marca uma tarefa existente como concluída. (FR-007, FR-010)

**Responses**:

| Status | Quando | Corpo |
|---|---|---|
| `200 OK` | Tarefa encontrada (pendente ou já concluída) | `Task` atualizada com `completed: true` |
| `404 Not Found` | Nenhuma tarefa com o `id` informado | `{ "message": "..." }` |

## DELETE /api/tasks/{id}

Exclui uma tarefa existente, independentemente do status. (FR-008)

**Responses**:

| Status | Quando | Corpo |
|---|---|---|
| `204 No Content` | Tarefa excluída com sucesso | (vazio) |
| `404 Not Found` | Nenhuma tarefa com o `id` informado | `{ "message": "..." }` |