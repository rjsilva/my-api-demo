# Contrato: Formato do Registro de Log

Contrato estável para quem consome os logs (suporte, consultas no Loki e testes automatizados).
O modelo completo de campos está em [data-model.md](../data-model.md) §2–3.

## Console (uma linha JSON por evento, ECS)

Os nomes de campo deste contrato (`log.level`, `http.route`, `error.type`...) são caminhos ECS. O
formatter ECS do Spring Boot escreve cada segmento como objeto aninhado, o que é equivalente no
ECS. Exemplos (formatados em várias linhas aqui; no console cada evento é uma única linha).

Requisição concluída:

```json
{
  "@timestamp": "2026-11-17T03:40:14.052125Z",
  "log": { "level": "INFO", "logger": "com.spec.demo_ia_spec.observability.RequestIdFilter" },
  "process": { "pid": 99324, "thread": { "name": "http-nio-8080-exec-3" } },
  "service": { "name": "demo-ia-spec" },
  "message": "HTTP request completed",
  "trace_id": "3b1ed42d3fd3a8d5260b9f0e812a40d6",
  "span_id": "076e735aeebd67b5",
  "request_id": "suporte-ticket-1234",
  "http": { "method": "POST", "route": "/api/tasks", "status_code": 201 },
  "duration_ms": 10,
  "ecs": { "version": "8.11" }
}
```

Erro inesperado:

```json
{
  "@timestamp": "2026-11-17T03:45:42.178009Z",
  "log": { "level": "ERROR", "logger": "com.spec.demo_ia_spec.exception.GlobalExceptionHandler" },
  "service": { "name": "demo-ia-spec" },
  "message": "Unexpected error while handling request",
  "trace_id": "dc9ae026b1da68b7e8127283a47b99cc",
  "span_id": "8ebcf40b39ddf382",
  "request_id": "b3bb0406-21d0-419b-8846-add978c5e9eb",
  "error": {
    "type": "java.lang.IllegalStateException",
    "message": "connection to jdbc:postgresql://***@db:5432/tasks failed",
    "stack_trace": "java.lang.IllegalStateException: connection to jdbc:postgresql://***@db:5432/tasks failed\n\tat …"
  },
  "http": { "method": "PATCH", "route": "/api/tasks/{id}/complete" }
}
```

## Garantias

1. Toda linha é JSON válido e contém `@timestamp`, `log.level`, `message`, `log.logger` e
   `service.name`.
2. Toda linha emitida durante uma requisição contém `request_id`, `trace_id` e `span_id`.
3. Nenhuma linha contém corpo, cabeçalhos, cookies, query string ou credenciais.
4. Nomes de campos são estáveis. Renomear um campo é uma mudança incompatível.

## Consultas no Loki (Grafana → Explore → Loki)

| Objetivo | LogQL |
|---|---|
| Todos os logs | `{service_name="demo-ia-spec"}` |
| Uma requisição | `{service_name="demo-ia-spec"} \| request_id="<valor>"` |
| Só erros | `{service_name="demo-ia-spec"} \| severity_text="ERROR"` |
| Um trace | `{service_name="demo-ia-spec"} \| trace_id="<valor>"` |
