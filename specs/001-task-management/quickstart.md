# Guia de Validação Rápida: Gerenciamento de Tarefas

**Contrato**: [contracts/tasks-api.md](./contracts/tasks-api.md)

## Pré-requisitos

- Java 21 instalado (ou use o toolchain do Gradle)
- Projeto na raiz do repositório `demo-ia-spec`

## Executar a aplicação

```bash
./gradlew bootRun
```

A API fica disponível em `http://localhost:8080`.

## Cenários de validação

### 1. Criar uma tarefa válida (US1)

```bash
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Comprar leite"}'
```

**Esperado**: `201 Created`, corpo com `id`, `title: "Comprar leite"`, `description: null`,
`completed: false`.

### 2. Rejeitar título ausente ou muito longo (US1, casos de borda)

```bash
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":""}'
```

**Esperado**: `400 Bad Request`.

### 3. Listar tarefas com pendentes antes de concluídas (US2)

```bash
curl -i http://localhost:8080/api/tasks
```

**Esperado**: `200 OK`, array com as tarefas pendentes antes das concluídas, cada grupo em
ordem de criação.

### 4. Marcar uma tarefa como concluída (US3)

```bash
curl -i -X PATCH http://localhost:8080/api/tasks/1/complete
```

**Esperado**: `200 OK`, `completed: true`. Repetir a mesma chamada deve continuar retornando
`200 OK` (idempotente, FR-010). Chamar com um `id` inexistente deve retornar `404 Not Found`.

### 5. Excluir uma tarefa (US4)

```bash
curl -i -X DELETE http://localhost:8080/api/tasks/1
```

**Esperado**: `204 No Content`. Uma listagem subsequente não deve mais incluir essa tarefa.
Chamar com um `id` inexistente deve retornar `404 Not Found`.

## Critério de aceitação da validação

Todos os 5 cenários acima devem se comportar conforme descrito antes de considerar a
funcionalidade pronta para revisão (ver Critérios de Sucesso em [spec.md](./spec.md)).