# Modelo de Dados (Fase 1): Logs Estruturados e Rastreáveis por Requisição

Esta funcionalidade **não altera o banco de dados** (nenhuma migração nova). As "entidades" abaixo
são estruturas em tempo de execução e formatos de saída.

## 1. Contexto da Requisição (`request_id`)

| Campo | Origem | Regra |
|---|---|---|
| `request_id` | Cabeçalho `X-Request-Id` do cliente, se válido; senão UUID v4 gerado | Válido se casar com `^[A-Za-z0-9._-]{1,128}$`. Valor inválido é descartado, sem erro |
| `trace_id` / `span_id` | Contexto W3C (`traceparent`) do cliente, se houver; senão gerado pelo tracing | Independente do `request_id` |

**Ciclo de vida**: criado na entrada do `RequestIdFilter`, posto no MDC (`request_id`) e como
atributo da requisição, devolvido no cabeçalho de resposta e removido do MDC no fim da
requisição (inclusive em exceção).

## 2. Registro de Log (console, formato ECS)

Campos obrigatórios em toda linha (FR-002):

| Campo JSON | Significado |
|---|---|
| `@timestamp` | Data/hora ISO-8601 com fuso |
| `log.level` | `TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR` |
| `message` | Mensagem fixa e legível (sem dados interpolados sensíveis) |
| `log.logger` | Componente de origem |
| `process.thread.name` | Thread |
| `service.name` | `demo-ia-spec` |

Campos presentes dentro de uma requisição (FR-004):

| Campo JSON | Significado |
|---|---|
| `request_id` | Identificador da requisição (MDC) |
| `trace_id`, `span_id` | Rastreamento ativo (MDC do Micrometer, renomeados) |

Campos por tipo de evento:

| Evento | Nível | Campos adicionais |
|---|---|---|
| Requisição concluída (FR-007) | `INFO` | `http.method`, `http.route`, `http.status_code`, `duration_ms` |
| Erro de negócio (FR-009) | `WARN` | `error.reason` (ex.: `task_not_found`, `validation_failed`), `task.id` ou `validation.fields` (só nomes) |
| Dependência indisponível (FR-010) | `ERROR` | `dependency` (`database`), `failure` (`unavailable`/`timeout`), `error.type`, `error.message` (sanitizada) |
| Erro inesperado (FR-008) | `ERROR` | `error.type`, `error.message` (sanitizada), `error.stack_trace`, `http.method`, `http.route` |

**Nunca presentes** (FR-011/FR-012): corpo de requisição/resposta, cabeçalhos, cookies, query
string, valores de campos de tarefa, credenciais ou string de conexão completa.

## 3. Registro de Log (OTLP → Loki)

O mesmo evento, exportado pelo `OpenTelemetryAppender`:
- `severity_text` / `severity_number`, `body` (= `message`);
- `trace_id` / `span_id` nativos do LogRecord;
- atributos: `request_id` (MDC) e os pares chave-valor acima;
- *resource*: `service.name=demo-ia-spec`, que vira o label `service_name` no Loki.

## 4. ErrorResponse (corpo de erro da API)

| Campo | Tipo | Mudança |
|---|---|---|
| `message` | string | Inalterado (FR-019) |
| `requestId` | string | **Novo**, igual ao cabeçalho `X-Request-Id` (FR-006) |

Detalhes em [contracts/request-id-http.md](./contracts/request-id-http.md).
