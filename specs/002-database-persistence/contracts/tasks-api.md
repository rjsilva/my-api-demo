# Contrato da API: Tarefas (alterações da 002)

**Contrato base**: [001 contracts/tasks-api.md](../../001-task-management/contracts/tasks-api.md)
**Modelo de dados**: [data-model.md](../data-model.md)

Todos os endpoints, corpos de requisição, representações de `Task`, códigos de status e
mensagens definidos no contrato da 001 **permanecem idênticos** (FR-007). Esta funcionalidade
apenas **acrescenta** uma resposta possível a cada endpoint.

## Nova resposta comum a todos os endpoints de `/api/tasks`

Aplica-se a `POST /api/tasks`, `GET /api/tasks`, `PATCH /api/tasks/{id}/complete` e
`DELETE /api/tasks/{id}`. (FR-009)

| Status | Quando | Corpo |
|---|---|---|
| `503 Service Unavailable` | Não foi possível acessar o armazenamento durante a operação | `{ "message": "Storage temporarily unavailable. Please try again later." }` |

Garantias:

- O formato do corpo é o mesmo `{ "message": "..." }` já usado para `400` e `404`.
- A mensagem é genérica: não contém nomes de host, SQL, stack traces ou nomes de exceção.
- Nenhuma alteração parcial é salva quando a resposta é `503` (FR-008).
- Erros que não são de indisponibilidade do armazenamento **não** viram `503`.

## Comportamentos que passam a valer entre reinícios

Sem mudança de formato, mas agora garantidos após reiniciar a aplicação (FR-001 a FR-006):

- `GET /api/tasks` devolve as mesmas tarefas, com os mesmos `id`, `title`, `description`,
  `completed` e `createdAt`, na mesma ordem.
- `PATCH`/`DELETE` em um `id` excluído antes do reinício continuam respondendo `404`.
- `POST /api/tasks` nunca devolve um `id` já usado por outra tarefa, existente ou excluída.
