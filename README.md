# demo-ia-spec

API REST de gerenciamento de tarefas (Kotlin + Spring Boot 4), com persistência em PostgreSQL.

## Pré-requisitos

- JDK 21 (o Gradle wrapper `./gradlew` cuida do resto)
- Docker com Docker Compose v2 (`docker compose version`) em execução
- Portas livres no host: `5432` (PostgreSQL), `3000` (Grafana), `4317` e `4318` (OTLP)

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

## Observabilidade local

O `compose.yaml` também sobe o serviço `lgtm` ([`grafana/otel-lgtm`](https://github.com/grafana/docker-otel-lgtm):
Grafana + Loki + Tempo + Prometheus). Com `docker compose up -d` (ou só `./gradlew bootRun`) a
aplicação já envia logs, traces e métricas via OTLP para ele, sem configuração extra. O contêiner
usa cerca de 1 GB de memória; pare-o com `docker compose stop lgtm` se não precisar dele.

- **Grafana**: http://localhost:3000 (sem login). Os logs ficam em *Explore → Loki*.
- **Console**: cada log é uma linha JSON (formato ECS) com `request_id`, `trace_id` e `span_id`.

### Identificador da requisição

Toda resposta traz o cabeçalho `X-Request-Id`, e os corpos de erro trazem o mesmo valor em
`requestId`. O cliente pode enviar o próprio `X-Request-Id` (até 128 caracteres `A-Z a-z 0-9 . _ -`,
ex.: número do ticket); valores inválidos são trocados por um UUID. Para investigar um problema,
peça esse valor ao usuário e pesquise no Loki.

```bash
curl -i -H 'X-Request-Id: suporte-1234' http://localhost:8080/api/tasks
```

### Consultas no Loki

| Objetivo | LogQL |
|---|---|
| Todos os logs | `{service_name="demo-ia-spec"}` |
| Uma requisição | `{service_name="demo-ia-spec"} \| request_id="suporte-1234"` |
| Só erros | `{service_name="demo-ia-spec"} \| severity_text="ERROR"` |
| Um trace | `{service_name="demo-ia-spec"} \| trace_id="<valor>"` |

Formato completo dos logs: [contrato de log](specs/003-structured-logging-observability/contracts/log-record.md).

### Variáveis de ambiente

| Variável | Padrão | Efeito |
|---|---|---|
| `LOG_LEVEL` | `INFO` | Nível de log da aplicação |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Fração das requisições com trace exportado (0.0 a 1.0) |
| `OTLP_EXPORT_TIMEOUT` | `5s` | Timeout de envio de logs, traces e métricas |
| `OTLP_CONNECT_TIMEOUT` | `2s` | Timeout de conexão com o coletor OTLP |
| `OTLP_METRICS_STEP` | `15s` | Intervalo de envio de métricas |

Se o `lgtm` estiver fora do ar, a API continua respondendo normalmente; a telemetria daquele
período é descartada e os logs continuam no console.

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

O teste ponta a ponta de exportação para o Loki (`LgtmLogExportIntegrationTest`, `@Tag("e2e")`)
sobe um contêiner `grafana/otel-lgtm` e fica fora da suíte padrão. Para incluí-lo:

```bash
./gradlew test -Pe2e
```
