# Implementation Plan: Logs Estruturados e Rastreáveis por Requisição

**Branch**: `feat003/observality` | **Date**: 2026-11-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-structured-logging-observability/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Os logs passam a sair em JSON (ECS) pelo suporte nativo do Spring Boot
(`logging.structured.format.console`). Um `RequestIdFilter` lê `X-Request-Id`, aceito se for
válido, ou gera um UUID. O valor vai para o MDC (`request_id`), é devolvido no cabeçalho de
resposta e entra no corpo de erro (`ErrorResponse.requestId`). O mesmo filtro emite um log de
acesso estruturado por requisição.

O `spring-boot-starter-opentelemetry` traz tracing W3C (`trace_id`/`span_id` em todo log) e
exportação OTLP. O `OpenTelemetryAppender` do Logback envia os logs para o contêiner
`grafana/otel-lgtm`, adicionado ao `compose.yaml` (3000 Grafana, 4317/4318 OTLP), onde são
pesquisados no Grafana via Loki. Erros são registrados com nível e contexto adequados. Mensagens
de exceção passam por um `LogSanitizer`, e nenhum corpo, cabeçalho ou credencial é registrado. A
exportação é assíncrona, com timeouts explícitos, e nunca afeta as requisições.

## Technical Context

**Language/Version**: Kotlin 2.3.21 sobre JVM, toolchain Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (web, validation, data-jpa, flyway, actuator,
docker-compose já existentes). Novas:
- `spring-boot-starter-opentelemetry`: Micrometer Tracing + bridge OTel + exportadores OTLP de
  traces, métricas e logs
- `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`, versão via
  `opentelemetry-instrumentation-bom` declarado no `build.gradle.kts`
- `net.ttddyy.observation:datasource-micrometer-spring-boot`: spans JDBC, versão declarada no
  `build.gradle.kts`
- Teste: `org.testcontainers:testcontainers-grafana` (`LgtmStackContainer`), só para o teste e2e

**Storage**: PostgreSQL 18 (inalterado; sem migração nova). Telemetria em dev: `grafana/otel-lgtm`
(Loki, Tempo, Prometheus e Grafana).

**Testing**: JUnit 5, MockMvc/`@WebMvcTest`, `OutputCaptureExtension` para inspecionar a saída
JSON, Testcontainers (Postgres; LGTM no e2e). Ver research.md §11.

**Target Platform**: Servidor JVM (Tomcat embutido). Desenvolvimento local com Docker Compose.

**Project Type**: Serviço web (API REST), projeto único

**Performance Goals**: Sobrecarga de logging e tracing imperceptível nas respostas. Com o coletor
fora do ar, nenhum aumento perceptível de latência (SC-007).

**Constraints**:
- Corpo de sucesso da API inalterado; `ErrorResponse` só ganha `requestId` (FR-019).
- Nenhum dado sensível ou corpo completo em logs, spans ou métricas (FR-011/012).
- Exportação OTLP não bloqueante, com timeouts configurados (Princípio VII).
- Um único comando para subir banco e Grafana (FR-016).

**Scale/Scope**: Baixo volume (demonstração, mono-usuário). Afeta todas as rotas (4 de tarefas,
health e rotas inexistentes). 1 serviço novo no compose.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação (pré-pesquisa) | Reavaliação (pós-design) |
|---|---|---|
| I. Layered Architecture | PASS: filtro e logging são preocupações HTTP/transversais, sem lógica de negócio | PASS: `RequestIdFilter`, `LogSanitizer` e a instalação do appender ficam no novo pacote `observability` (transversal, como `exception` e `dto`); `GlobalExceptionHandler` continua só HTTP; service e repository não mudam |
| II. Test-First Development | PASS: tasks.md exigirá testes antes de cada mudança | PASS: cada FR/SC tem teste definido (research.md §11), incluindo concorrência (SC-002), dados sensíveis (SC-004) e coletor fora do ar (SC-007) |
| III. Spec-Driven Development | PASS: specify → plan | PASS: spec ajustada (FR-003 e premissa) para refletir a escolha de `X-Request-Id` |
| IV. English Code, Portuguese Documentation | PASS | PASS: classes, chaves de log (`request_id`, `http.route`) e mensagens de log em inglês; spec, plano e quickstart em português; README ganha seção em português |
| V. Technology Stack Consistency | PASS: continua Kotlin + Boot 4 + Gradle KTS; OTel e Grafana são exigidos pela própria constituição | PASS: versões fora do BOM do Boot (OTel instrumentation BOM, datasource-micrometer) declaradas centralmente no `build.gradle.kts` |
| VI. Observability | ATENÇÃO: o usuário pediu `X-Request-Id` como ID de correlação; a constituição descreve o ID como propagado via `traceparent` | PASS com desvio justificado (Complexity Tracking): `traceparent` é propagado e `trace_id`/`span_id` estão em todo log; o ID de correlação vem de `X-Request-Id` e é devolvido na resposta. Logs, métricas (RED + `logback.events`) e spans (HTTP + JDBC) descritos abaixo |
| VII. Resilience | PASS: nova dependência (coletor OTLP) identificada | PASS: timeouts explícitos e comportamento de falha definidos (tabela abaixo e research.md §10), com teste de falha |

### Observabilidade desta funcionalidade (exigida pelo Workflow)

- **Logs**: formato e campos em [contracts/log-record.md](./contracts/log-record.md). Eventos:
  requisição concluída (`INFO`), erro de negócio (`WARN`), dependência indisponível (`ERROR`) e
  erro inesperado (`ERROR` com pilha sanitizada).
- **Métricas**: `http.server.requests` (RED), `logback.events` (logs por nível), `hikaricp.*` e
  `jdbc.*`. Nenhuma tag com `request_id` ou dados de usuário.
- **Spans**: span HTTP de servidor por requisição (atributo `request.id`, rota-padrão, status) e
  spans JDBC de conexão e query (sem parâmetros). Span com status de erro em 5xx.

### Dependências externas (Princípio VII)

| Dependência | Timeouts | Comportamento de falha | Teste |
|---|---|---|---|
| PostgreSQL (existente) | Inalterado: `connection-timeout` 5 s, `initialization-fail-timeout` 30 s | Inalterado: `503` genérico, agora com `requestId`, e log `ERROR` com `dependency=database` | Existente, ampliado para verificar o log e o `requestId` |
| Coletor OTLP (otel-lgtm) | Logs e traces: export 5 s, conexão 2 s; métricas: connect 2 s, read 5 s, intervalo 15 s (externalizados no `application.yaml`) | Descarta o lote; fila limitada; nunca bloqueia nem falha a requisição; o console continua | `TelemetryExporterDownIntegrationTest` |

## Project Structure

### Documentation (this feature)

```text
specs/003-structured-logging-observability/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões técnicas
├── data-model.md        # Fase 1: contexto da requisição, campos de log, ErrorResponse
├── quickstart.md        # Fase 1: guia de validação ponta a ponta
├── contracts/
│   ├── request-id-http.md   # Cabeçalho X-Request-Id e corpo de erro
│   └── log-record.md        # Formato do log e consultas Loki
├── checklists/requirements.md
└── tasks.md             # Fase 2 (/speckit-tasks; não criado aqui)
```

### Source Code (repository root)

```text
compose.yaml                                   # + serviço lgtm (grafana/otel-lgtm, 3000/4317/4318)
build.gradle.kts                               # + starter-opentelemetry, logback appender, datasource-micrometer, BOM OTel
README.md                                      # + seção "Observabilidade local" (pt-BR)

src/main/kotlin/com/spec/demo_ia_spec/
├── observability/                             # NOVO
│   ├── RequestIdFilter.kt                     # X-Request-Id → MDC/resposta; log de acesso
│   ├── RequestIdSpanTagger.kt                 # interceptor: atributo request.id no span HTTP
│   ├── LogSanitizer.kt                        # máscara de padrões sensíveis
│   ├── SanitizedException.kt                  # cópia da exceção com mensagens sanitizadas
│   ├── OpenTelemetryAppenderInstaller.kt      # OpenTelemetryAppender.install(openTelemetry)
│   └── JdbcObservationConfig.kt               # nome do datasource nos spans JDBC sem abrir conexão
├── exception/
│   ├── ErrorResponse.kt                       # + requestId
│   ├── TaskNotFoundException.kt               # expõe id (campo task.id do log)
│   └── GlobalExceptionHandler.kt              # níveis WARN/ERROR, handler genérico 500, sanitização
├── controller/ service/ repository/ dto/ model/   # inalterados

src/main/resources/
├── application.yaml                           # logging.structured.*, tracing, OTLP + timeouts, server.error.*
└── logback-spring.xml                         # NOVO: CONSOLE (StructuredLogEncoder do Boot) + appender OpenTelemetry

src/test/kotlin/com/spec/demo_ia_spec/
├── support/LogLines.kt                         # parse das linhas JSON capturadas (caminhos ECS)
├── observability/
│   ├── RequestIdFilterTest.kt
│   ├── RequestIdSpanTaggerTest.kt
│   ├── LogSanitizerTest.kt
│   ├── SanitizedExceptionTest.kt
│   ├── OpenTelemetryAppenderInstallerTest.kt
│   └── JdbcObservationConfigTest.kt
├── exception/GlobalExceptionHandlerTest.kt     # ampliado
└── integration/
    ├── StructuredConsoleFormatIntegrationTest.kt
    ├── RequestIdHeaderIntegrationTest.kt
    ├── StructuredLoggingIntegrationTest.kt
    ├── ConcurrentRequestIdIntegrationTest.kt
    ├── SensitiveDataLoggingIntegrationTest.kt
    ├── UnexpectedErrorResponseIntegrationTest.kt
    ├── TelemetryExporterDownIntegrationTest.kt
    └── LgtmLogExportIntegrationTest.kt         # tag e2e

src/test/resources/application-test.yaml        # perfil "test": exportação OTLP desligada por padrão
```

**Divergências da implementação** (detalhes em research.md §13):
- `RequestIdFilter` roda em `HIGHEST_PRECEDENCE + 2`, logo depois do `ServerHttpObservationFilter`,
  para o log de acesso ter `trace_id`/`span_id`; o cabeçalho continua sendo escrito antes da cadeia.
- O console ECS do Boot 4 usa objetos aninhados (`{"http":{"route":...}}`); os nomes do contrato
  são caminhos ECS.
- O erro inesperado registra `error.type`/`error.message`/`error.stack_trace` como pares
  chave-valor (pilha gerada da `SanitizedException`), para `error.type` mostrar a classe original.
- `TypeMismatchException` ganhou handler 400, para não virar 500 no handler genérico.
- `JdbcObservationConfig` evita que o datasource-micrometer abra conexão na inicialização (que
  dobrava a espera de 30 s do FR-010 da 002 com o banco fora do ar).

**Structure Decision**: Projeto único existente. A funcionalidade é transversal, então o código
novo vai para um pacote `observability`, ao lado de `exception`. Controller, service e repository
não mudam, preservando o Princípio I.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Princípio VI: o ID de correlação vem do cabeçalho `X-Request-Id` (ou UUID), não do `traceparent` | Escolha explícita do usuário. Um ID curto e legível, que o cliente pode definir (ex.: número do ticket), é mais prático para suporte do que um trace ID de 32 hex | Usar só o `trace_id` como ID devolvido não deixa o cliente escolher o valor. Mesmo assim o `traceparent` é propagado e `trace_id`/`span_id` estão em todo log, então a rastreabilidade W3C exigida continua atendida |
| Dependência fora do BOM do Boot (`datasource-micrometer-spring-boot`) | O Princípio VI exige spans para chamadas ao banco, e o Boot 4 não instrumenta JDBC sozinho | Sem ela, as queries ficariam fora do trace (viola o VI). O Java agent do OTel mudaria a forma de executar a aplicação |
