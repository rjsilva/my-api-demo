# Quickstart: Persistência Durável de Tarefas

**Spec**: [spec.md](./spec.md) | **Contratos**: [tasks-api.md](./contracts/tasks-api.md), [health-api.md](./contracts/health-api.md)

Guia de validação ponta a ponta. Os formatos de requisição e resposta estão nos contratos; aqui
ficam só os passos e os resultados esperados.

## Pré-requisitos

- JDK 21 (o Gradle wrapper cuida do resto).
- Docker com Docker Compose v2 (`docker compose version`) em execução.
- Porta `5432` livre no host (pare um Postgres local, se houver).

## 1. Subir o banco com um único comando (US3, FR-011)

```bash
docker compose up -d
docker compose ps        # serviço do postgres deve ficar "healthy"
```

**Esperado**: em menos de 2 minutos a partir de um clone limpo (SC-003), sem nenhuma outra
configuração.

Alternativa: `./gradlew bootRun` sobe o banco automaticamente antes de iniciar a aplicação
(suporte a Docker Compose do Spring Boot).

## 2. Iniciar a aplicação e conferir a saúde

```bash
./gradlew bootRun
curl -s localhost:8080/actuator/health
```

**Esperado**: `200` com `"status": "UP"` e `components.db.status = "UP"`. Nos logs, o Flyway
informa a aplicação da migração `V1` na primeira execução (e "Schema is up to date" nas
seguintes) — FR-013.

## 3. Persistência entre reinícios (US1, SC-001)

1. Crie as tarefas "A" e "B" e uma terceira "C" (`POST /api/tasks`).
2. Conclua "A" (`PATCH /api/tasks/{idA}/complete`) e exclua "B" (`DELETE /api/tasks/{idB}`).
3. Anote o resultado de `GET /api/tasks`.
4. Pare a aplicação (Ctrl+C) e inicie de novo com `./gradlew bootRun`.
5. Repita `GET /api/tasks`.

**Esperado**: resposta idêntica à do passo 3 (mesmos `id`, `createdAt`, status e ordem: "C"
antes de "A"); "B" ausente; `PATCH`/`DELETE` em `idB` → `404`; uma nova tarefa recebe `id`
maior que todos os anteriores, inclusive o de "B".

## 4. Dados sobrevivem a parar e subir o banco (FR-012)

```bash
docker compose down      # para e remove o container, mantém o volume
docker compose up -d
```

**Esperado**: após reiniciar a aplicação, `GET /api/tasks` devolve as mesmas tarefas.

## 5. Banco fora do ar durante a operação (FR-009)

Com a aplicação rodando: `docker compose stop`, depois `GET /api/tasks`.

**Esperado**: em até ~5 s, `503` com `{ "message": "Storage temporarily unavailable. Please try again later." }`;
`/actuator/health` → `503` com `db: DOWN`. Após `docker compose start`, as requisições voltam a
funcionar sem reiniciar a aplicação.

## 6. Banco indisponível na inicialização (FR-010)

1. `docker compose stop` e inicie a aplicação **sem** o suporte a Compose:
   `java -jar build/libs/demo-ia-spec-0.0.1-SNAPSHOT.jar` (após `./gradlew bootJar`).
   **Esperado**: a aplicação não aceita requisições, e após ~30 s encerra com erro de conexão
   ao banco nos logs.
2. Repita, mas rode `docker compose start` cerca de 10 s depois de iniciar a aplicação.
   **Esperado**: a aplicação conclui a inicialização normalmente.

## 7. Testes automatizados (FR-016, SC-002)

```bash
./gradlew test
```

**Esperado**: todos os testes passam, incluindo os cenários de aceitação da 001 e o teste de
reinício. Os testes sobem e descartam o próprio Postgres (Testcontainers) e **não** dependem
do banco do passo 1 (pode estar parado). Docker precisa estar em execução.

## 8. Recomeçar do zero (FR-014)

```bash
docker compose down -v   # remove container E volume: apaga todas as tarefas locais
```
