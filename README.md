# demo-ia-spec

API REST de gerenciamento de tarefas (Kotlin + Spring Boot 4), com persistência em PostgreSQL.

## Pré-requisitos

- JDK 21 (o Gradle wrapper `./gradlew` cuida do resto)
- Docker com Docker Compose v2 (`docker compose version`) em execução
- Porta `5432` livre no host (pare um PostgreSQL local, se houver)

## Banco de dados de desenvolvimento

O banco é definido em [`compose.yaml`](./compose.yaml) (PostgreSQL 18, dados guardados no volume
`tasks-db-data`).

| Ação | Comando | Dados |
|---|---|---|
| Subir o banco | `docker compose up -d` | — |
| Ver o estado (deve ficar `healthy`) | `docker compose ps` | — |
| Parar o banco | `docker compose stop` ou `docker compose down` | **mantidos** |
| Apagar os dados e recomeçar do zero | `docker compose down -v` | **apagados** |

O schema é criado e atualizado automaticamente pelo Flyway na inicialização da aplicação, a
partir dos scripts em `src/main/resources/db/migration`. Scripts já aplicados nunca devem ser
editados: mudanças de schema entram como um novo `V<n>__descricao.sql`.

## Rodando a aplicação

```bash
./gradlew bootRun
```

O `bootRun` sobe o banco do `compose.yaml` automaticamente, se ele ainda não estiver rodando
(suporte a Docker Compose do Spring Boot). A API fica em `http://localhost:8080`.

Para rodar fora do `bootRun` (ex.: `java -jar build/libs/demo-ia-spec-0.0.1-SNAPSHOT.jar`), suba o
banco antes com `docker compose up -d`. A conexão pode ser trocada pelas variáveis `DB_URL`,
`DB_USERNAME` e `DB_PASSWORD`.

Se o banco não estiver acessível na inicialização, a aplicação tenta por até 30 segundos e
então encerra com erro de conexão. Se o banco cair com a aplicação rodando, as requisições
respondem `503` até ele voltar.

## Endpoints

- API de tarefas (`/api/tasks`): ver [contrato](specs/001-task-management/contracts/tasks-api.md)
  e [alterações da 002](specs/002-database-persistence/contracts/tasks-api.md)
- Saúde da aplicação e do banco: `GET /actuator/health`

## Testes

```bash
./gradlew test
```

Os testes de integração sobem o próprio PostgreSQL (Testcontainers) e **não** usam o banco de
desenvolvimento, mas exigem Docker em execução. O teste `StartupWithoutDatabaseTest` leva cerca de
30 segundos (marcado com `@Tag("slow")`).
