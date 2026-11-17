---
description: "Task list for Logs Estruturados e Rastreáveis por Requisição"
---

# Tasks: Logs Estruturados e Rastreáveis por Requisição

**Input**: Design documents from `/specs/003-structured-logging-observability/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/request-id-http.md,
contracts/log-record.md, quickstart.md

**Tests**: Incluídas e obrigatórias. A constituição (Princípio II, NON-NEGOTIABLE) exige TDD:
cada teste JUnit 5 deve ser escrito e **falhar** antes da implementação correspondente. A
exceção são tarefas de configuração sem lógica (`compose.yaml`, `build.gradle.kts`, README),
validadas pelo quickstart.

**Organization**: Tarefas agrupadas por história de usuário (US1–US4 da spec.md).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: História de usuário da tarefa (US1, US2, US3, US4)

## Path Conventions

Projeto único. Pacote base `com.spec.demo_ia_spec`:
- código em `src/main/kotlin/com/spec/demo_ia_spec/`;
- testes em `src/test/kotlin/com/spec/demo_ia_spec/`;
- recursos em `src/main/resources/` e `src/test/resources/`.

Testes de integração precisam de **Docker em execução** (Testcontainers; usar
`@Import(TestcontainersConfiguration::class)` como os testes existentes). Código, chaves de log
e mensagens em inglês; documentação em português (Princípio IV).

**Nomes a confirmar**: propriedades OTLP do Boot 4.1 e tag da imagem `grafana/otel-lgtm` estão
marcadas "confirmar na implementação" em research.md. Conferir na documentação do Spring Boot
4.1.2 antes de usar cada uma (ver T037).

---

## Phase 1: Setup

**Purpose**: Dependências, contêiner de telemetria e perfil de testes

- [X] T001 Atualizar `build.gradle.kts`:
  - **`implementation`:** `org.springframework.boot:spring-boot-starter-opentelemetry`,
    `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` e
    `net.ttddyy.observation:datasource-micrometer-spring-boot` (versão explícita compatível com
    Boot 4, declarada numa `val` no topo do arquivo).
  - **BOM:** importar `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom` no
    bloco `dependencyManagement { imports { mavenBom(...) } }`, com versão compatível com o SDK
    OTel gerido pelo Boot 4.1.2 (versão numa `val` no topo). O appender fica sem versão.
  - **`testImplementation`:** `org.testcontainers:testcontainers-grafana`.
  - **`tasks.withType<Test>`:** `systemProperty("spring.profiles.active", "test")`. Excluir a
    tag JUnit `e2e` por padrão (`useJUnitPlatform { if (!project.hasProperty("e2e"))
    excludeTags("e2e") }`).
  - Rodar `./gradlew dependencies --configuration runtimeClasspath` e confirmar que resolve.
- [X] T002 [P] Adicionar o serviço `lgtm` a `compose.yaml`, mantendo `postgres` inalterado:
  - `image: grafana/otel-lgtm:<tag fixa estável>` (nunca `latest`);
  - `ports: ["3000:3000", "4317:4317", "4318:4318"]`;
  - `healthcheck` com `curl -f http://localhost:3000/api/health` (interval 5s, timeout 5s,
    retries 20).
  - Validar com `docker compose config`.
- [X] T003 [P] Criar `src/test/resources/application-test.yaml` desligando toda exportação OTLP
  nos testes padrão: métricas (`management.otlp.metrics.export.enabled: false`), traces e logs
  (propriedades de export/enabled do Boot 4.1; confirmar nomes). Assim nenhum teste tenta
  conectar em `localhost:4318`.
- [X] T004 Rodar `./gradlew test` e confirmar que a suíte existente continua verde com as novas
  dependências e o perfil `test` (depende de T001, T003).

**Checkpoint**: build resolve, `docker compose up -d` sobe `postgres` e `lgtm` healthy, suíte
existente verde.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Formato JSON dos logs e utilitário de testes usado por US2–US4

**⚠️ CRITICAL**: Nenhuma história pode começar antes desta fase

- [X] T005 Criar o utilitário de teste `src/test/kotlin/com/spec/demo_ia_spec/support/LogLines.kt`:
  - função `parseJsonLogLines(output: CapturedOutput): List<JsonNode>`, que lê cada linha
    não vazia de `output.out` com Jackson e **falha** o teste se alguma linha não for JSON
    válido;
  - helper `List<JsonNode>.withRequestId(id: String)`.
- [X] T006 Escrever o teste `src/test/kotlin/com/spec/demo_ia_spec/integration/StructuredConsoleFormatIntegrationTest.kt`
  (`@SpringBootTest`, `@ExtendWith(OutputCaptureExtension::class)`, Testcontainers):
  - todas as linhas emitidas na inicialização são JSON (via T005);
  - cada linha contém `@timestamp`, `log.level`, `message`, `log.logger` e
    `service.name == "demo-ia-spec"`;
  - linhas fora de requisição **não** têm `request_id`.
  - Deve FALHAR.
- [X] T007 Configurar `src/main/resources/application.yaml` (faz T006 passar):
  - `logging.structured.format.console: ecs`;
  - `logging.structured.json.rename: { traceId: trace_id, spanId: span_id }`;
  - `logging.level.root: ${LOG_LEVEL:INFO}` (FR-018);
  - `management.tracing.sampling.probability: ${TRACING_SAMPLING_PROBABILITY:1.0}`;
  - `server.error.include-stacktrace: never` e `server.error.include-message: never`.

**Checkpoint**: aplicação inicia emitindo só JSON ECS; T006 verde.

---

## Phase 3: User Story 1 - Identificador da Requisição Devolvido ao Cliente (Priority: P1) 🎯 MVP

**Goal**: Toda resposta (sucesso ou erro) traz `X-Request-Id`, e os corpos de erro trazem
`requestId` ([contracts/request-id-http.md](./contracts/request-id-http.md)).

**Independent Test**: Chamadas a qualquer rota retornam `X-Request-Id`. Um valor válido do
cliente é reaproveitado, um inválido é trocado por UUID, e 400/404/503 trazem `requestId` no
corpo igual ao cabeçalho.

### Tests for User Story 1 ⚠️ (escrever primeiro; devem FALHAR)

- [X] T008 [P] [US1] Escrever `src/test/kotlin/com/spec/demo_ia_spec/observability/RequestIdFilterTest.kt`
  (JUnit 5 puro, `MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain`), com
  um caso por regra:
  - sem cabeçalho: resposta tem `X-Request-Id` que é UUID válido (`UUID.fromString` não lança);
  - `X-Request-Id: suporte-1234`: reaproveitado igual;
  - valor com 128 caracteres `[A-Za-z0-9._-]`: aceito;
  - valor com 129 caracteres, vazio, com espaço, ou com `\r\n`: descartado, e gera UUID (regra
    `^[A-Za-z0-9._-]{1,128}$`);
  - durante `chain.doFilter`, `MDC.get("request_id")` é o valor efetivo e o atributo de
    requisição `RequestIdFilter.ATTRIBUTE` também;
  - depois do filtro, `MDC.get("request_id")` é `null`, inclusive quando a cadeia lança exceção;
  - o cabeçalho já está na resposta antes da cadeia rodar (verificar dentro de um
    `FilterChain` fake).
- [X] T009 [P] [US1] Ampliar `src/test/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandlerTest.kt`:
  - com um `MockHttpServletRequest` que tem `RequestIdFilter.ATTRIBUTE = "req-1"`, os
    handlers de 404, 400 e 503 retornam corpo com `requestId == "req-1"` e `message` inalterada
    (mesmos textos dos testes atuais);
  - sem o atributo, `requestId` não é nulo (fallback: UUID gerado na hora).
- [X] T010 [P] [US1] Escrever `src/test/kotlin/com/spec/demo_ia_spec/integration/RequestIdHeaderIntegrationTest.kt`
  (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers, cliente HTTP como em
  `TaskApiIntegrationTest`). Verifica `X-Request-Id` na resposta de:
  - `POST /api/tasks` (201), `GET /api/tasks` (200), `PATCH /api/tasks/{id}/complete` (200)
    e `DELETE /api/tasks/{id}` (204);
  - `POST` com título vazio (400) e `DELETE /api/tasks/999999` (404): corpo com
    `requestId == cabeçalho`;
  - `GET /rota-inexistente` (404) e `GET /actuator/health` (200);
  - duas chamadas iguais sem cabeçalho devolvem IDs diferentes;
  - enviar `X-Request-Id: suporte-1234` devolve `suporte-1234`.

### Implementation for User Story 1

- [X] T011 [US1] Criar `src/main/kotlin/com/spec/demo_ia_spec/observability/RequestIdFilter.kt`
  (`OncePerRequestFilter`, `@Component`, `@Order(Ordered.HIGHEST_PRECEDENCE)`):
  - constantes `HEADER = "X-Request-Id"`, `MDC_KEY = "request_id"`,
    `ATTRIBUTE = RequestIdFilter::class.java.name + ".requestId"` e
    `VALID = Regex("^[A-Za-z0-9._-]{1,128}$")`;
  - fluxo: resolver o ID (cabeçalho válido ou `UUID.randomUUID().toString()`), depois
    `response.setHeader(HEADER, id)`, `request.setAttribute(ATTRIBUTE, id)` e
    `MDC.put(MDC_KEY, id)`;
  - depois `chain.doFilter` em `try`, e `MDC.remove(MDC_KEY)` em `finally`.
  - Faz T008 passar.
- [X] T012 [US1] Alterar `src/main/kotlin/com/spec/demo_ia_spec/exception/ErrorResponse.kt` para
  `data class ErrorResponse(val message: String, val requestId: String)`.
- [X] T013 [US1] Alterar `src/main/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandler.kt`:
  - cada handler recebe `request: HttpServletRequest`;
  - preenche `requestId` com `request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String ?:
    UUID.randomUUID().toString()`;
  - mensagens existentes inalteradas.
  - Faz T009 e T010 passar (depende de T011, T012).
- [X] T014 [US1] Rodar `./gradlew test` e corrigir testes existentes que constroem
  `ErrorResponse(...)` ou comparam o corpo de erro inteiro (por exemplo `TaskControllerTest`),
  mantendo as asserções de `message` (FR-019).

**Checkpoint**: US1 completa. Quickstart §3 passa.

---

## Phase 4: User Story 2 - Logs Estruturados e Correlacionados (Priority: P1)

**Goal**: Todo log emitido durante uma requisição carrega `request_id`, `trace_id` e
`span_id`, e cada requisição gera um log de acesso estruturado
([contracts/log-record.md](./contracts/log-record.md)).

**Independent Test**: Capturar a saída de uma chamada. Todas as linhas dela têm o mesmo
`request_id` devolvido e o mesmo `trace_id`, mais uma linha `HTTP request completed` com
método, rota, status e duração. Com 50 chamadas simultâneas, nada se mistura.

### Tests for User Story 2 ⚠️ (escrever primeiro; devem FALHAR)

- [X] T015 [P] [US2] Escrever `src/test/kotlin/com/spec/demo_ia_spec/integration/StructuredLoggingIntegrationTest.kt`
  (`RANDOM_PORT`, `OutputCaptureExtension`, Testcontainers, utilitário T005):
  - `POST /api/tasks` com `X-Request-Id: it-create-1`: existe linha com
    `message == "HTTP request completed"`, `request_id == "it-create-1"`,
    `http.method == "POST"`, `http.route == "/api/tasks"`, `http.status_code == 201` e
    `duration_ms >= 0`;
  - todas as linhas com `request_id == "it-create-1"` têm o mesmo `trace_id` (32 hex) e
    `span_id` não vazio;
  - `PATCH /api/tasks/{id}/complete`: `http.route == "/api/tasks/{id}/complete"` (padrão, não
    o id real);
  - rota inexistente: `http.route == "UNMATCHED"` e `http.status_code == 404`;
  - com `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`, as linhas da
    requisição têm `trace_id == "4bf92f3577b34da6a3ce929d0e0e4736"`;
  - com `traceparent` malformado, a requisição responde 200 e tem um `trace_id` novo.
- [X] T016 [P] [US2] Escrever `src/test/kotlin/com/spec/demo_ia_spec/integration/ConcurrentRequestIdIntegrationTest.kt`:
  - disparar 50 `GET /api/tasks` em paralelo (`Executors.newFixedThreadPool(16)`), cada um com
    `X-Request-Id: conc-<n>`;
  - cada resposta devolve o próprio ID;
  - para cada `conc-<n>` há exatamente uma linha `HTTP request completed`;
  - todas as linhas com um mesmo `trace_id` compartilham um único `request_id` (SC-002).
- [X] T017 [P] [US2] Ampliar `RequestIdFilterTest.kt` (T008): depois da cadeia, o filtro emite
  um evento `INFO` `"HTTP request completed"` com pares chave-valor `http.method`,
  `http.route` (de `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, ou `"UNMATCHED"`),
  `http.status_code` e `duration_ms`, e **sem** query string. Verificar com um
  `ListAppender` do Logback anexado ao logger do filtro, lendo `ILoggingEvent.keyValuePairs`.

### Implementation for User Story 2

- [X] T018 [US2] Em `RequestIdFilter.kt`:
  - no `finally`, antes de `MDC.remove`, emitir
    `log.atInfo().setMessage("HTTP request completed").addKeyValue("http.method", ...)
    .addKeyValue("http.route", ...).addKeyValue("http.status_code", response.status)
    .addKeyValue("duration_ms", ...).log()` (SLF4J 2 fluent API; nunca concatenar);
  - `duration_ms` via `System.nanoTime()`.
  - Faz T017 e parte de T015 passar.
- [X] T019 [US2] Em `RequestIdFilter.kt`, injetar `ObjectProvider<io.micrometer.tracing.Tracer>`
  e, se houver span atual, `tracer.currentSpan()?.tag("request.id", id)`. O filtro roda antes
  do `ServerHttpObservationFilter`, então o span ainda não existe na entrada. Fazer a marcação
  num segundo filtro leve ou num `HandlerInterceptor.preHandle`:
  `src/main/kotlin/com/spec/demo_ia_spec/observability/RequestIdSpanTagger.kt`
  (`WebMvcConfigurer` + interceptor). Cobrir com um teste unitário
  `src/test/kotlin/com/spec/demo_ia_spec/observability/RequestIdSpanTaggerTest.kt` usando
  `SimpleTracer` (`io.micrometer:micrometer-tracing-test`; adicionar a `testImplementation` em
  `build.gradle.kts` se necessário), escrito antes.
- [X] T020 [US2] Rodar T015/T016 e ajustar até passar:
  - se `trace_id` não aparecer, conferir que o `spring-boot-starter-opentelemetry` registra o
    MDC do Micrometer e que o `rename` de T007 bate com as chaves reais (`traceId`/`spanId`);
  - se o log de acesso sair sem `trace_id`, emiti-lo **antes** de o span HTTP fechar: mover a
    ordem do `RequestIdFilter` para depois do `ServerHttpObservationFilter` (ex.:
    `Ordered.HIGHEST_PRECEDENCE + 2`), mantendo a escrita do cabeçalho antes da cadeia. Rodar
    T008/T010 de novo.

**Checkpoint**: US1 + US2 = MVP de suporte. Quickstart §4 passa.

---

## Phase 5: User Story 3 - Erros com Contexto e sem Dados Sensíveis (Priority: P2)

**Goal**: Erros registrados com nível e contexto adequados (research.md §7), mensagens
sanitizadas, nada sensível nos logs e 500 genérico ao cliente.

**Independent Test**: Provocar 400, 404, banco fora (503) e erro inesperado (500). Os níveis,
campos e mensagens seguem data-model.md §2. Enviar `Authorization`, `Cookie` e senha: nada
disso aparece na saída.

### Tests for User Story 3 ⚠️ (escrever primeiro; devem FALHAR)

- [X] T021 [P] [US3] Escrever `src/test/kotlin/com/spec/demo_ia_spec/observability/LogSanitizerTest.kt`.
  `LogSanitizer.sanitize(text: String?): String?` troca o valor por `***` em:
  - `password=abc` e `pwd=abc` (com `=` ou `:`; sem diferenciar maiúsculas);
  - `token=abc`, `secret=abc` e `api_key=abc`;
  - `Authorization: Basic xyz` e `Bearer eyJhbGci...`;
  - `jdbc:postgresql://user:pass@host:5432/db`, que vira `jdbc:postgresql://***@host:5432/db`.
  Também: texto sem segredo fica inalterado e `null` continua `null`.
- [X] T022 [P] [US3] Escrever `src/test/kotlin/com/spec/demo_ia_spec/observability/SanitizedExceptionTest.kt`.
  `SanitizedException.of(ex: Throwable)`:
  - mantém `stackTrace` igual ao original;
  - `message` sanitizada;
  - `toString()` começa com o nome da classe original (para `error.type` ficar útil);
  - `cause` também embrulhada e sanitizada, recursivamente, com proteção contra ciclos.
- [X] T023 [P] [US3] Ampliar `GlobalExceptionHandlerTest.kt` com `ListAppender` no logger do
  handler:
  - **404**: um evento `WARN`, `throwableProxy == null`, KV `error.reason=task_not_found` e
    `task.id`;
  - **400**: `WARN` com `error.reason=validation_failed` e `validation.fields` (só nomes dos
    campos; o valor rejeitado **não** aparece em nenhum lugar do evento);
  - **503**: `ERROR` com `dependency=database`, `failure=unavailable` (ou `timeout` quando a
    causa for `SQLTransientConnectionException`/timeout) e mensagem sanitizada (exceção com
    `password=abc` na mensagem não deixa `abc` no evento);
  - **Exceção inesperada** (`IllegalStateException("boom password=abc")`): resposta 500 com
    `message == "Unexpected error. Please contact support with the request id."` e
    `requestId`; evento `ERROR` com `throwableProxy` presente (pilha), classe original no
    `toString`, mensagem sem `abc`, e KV `http.method` e `http.route`;
  - **Exceções padrão do Spring MVC** (`NoResourceFoundException`,
    `HttpRequestMethodNotSupportedException`) mantêm o status próprio (404/405) e **não** viram
    500;
  - **`HttpMessageNotReadableException`** (JSON malformado) vira 400.
- [X] T024 [P] [US3] Escrever `src/test/kotlin/com/spec/demo_ia_spec/integration/SensitiveDataLoggingIntegrationTest.kt`
  (`RANDOM_PORT`, `OutputCaptureExtension`, Testcontainers):
  - enviar `GET /api/tasks?password=s3cr3t-q` com `Authorization: Bearer s3cr3t-token` e
    `Cookie: session=s3cr3t-cookie`;
  - enviar `POST /api/tasks` com corpo `{"title":"s3cr3t-title","description":"s3cr3t-desc"}`;
  - enviar `POST` inválido com título de 101 caracteres contendo `s3cr3t-long`;
  - `output.out` não contém nenhuma string `s3cr3t-*` (SC-004, FR-011/012).
- [X] T025 [P] [US3] Escrever `src/test/kotlin/com/spec/demo_ia_spec/integration/UnexpectedErrorResponseIntegrationTest.kt`:
  - registrar via `@TestConfiguration` um `@RestController` de teste
    (`GET /test/boom` lança `IllegalStateException("boom")`);
  - resposta 500 com corpo exatamente `{message, requestId}`, sem `trace`, `exception`, `boom`
    ou nome de classe;
  - cabeçalho `X-Request-Id` presente;
  - linha `ERROR` na saída com `error.type`, `error.stack_trace` e o mesmo `request_id` (FR-008,
    FR-013, SC-005).

### Implementation for User Story 3

- [X] T026 [P] [US3] Criar `src/main/kotlin/com/spec/demo_ia_spec/observability/LogSanitizer.kt`
  (`object`) com as regex de T021, aplicadas em ordem e sem diferenciar maiúsculas. Faz T021
  passar.
- [X] T027 [US3] Criar `src/main/kotlin/com/spec/demo_ia_spec/observability/SanitizedException.kt`
  (`class SanitizedException private constructor(originalClass: String, message: String?,
  cause: Throwable?) : RuntimeException(message, cause)`):
  - `toString()` = `"$originalClass: $message"`;
  - `companion fun of(ex: Throwable)` copia o `stackTrace` com `setStackTrace` e usa um
    `IdentityHashMap` para evitar ciclos.
  - Faz T022 passar (depende de T026).
- [X] T028 [US3] Alterar `GlobalExceptionHandler.kt` (depende de T013, T026, T027):
  - **`TaskNotFoundException`:** `log.atWarn().setMessage("Task not found")
    .addKeyValue("error.reason","task_not_found").addKeyValue("task.id", id).log()`. Expor
    `val id: Long` em `TaskNotFoundException.kt`.
  - **Validação:** `WARN` `"Request validation failed"` com
    `error.reason=validation_failed` e `validation.fields` = nomes dos campos separados por
    vírgula.
  - **503:** trocar o `log.warn` atual por `ERROR` `"Dependency unavailable"` com
    `dependency=database`, `failure` (`timeout` se alguma causa for
    `SQLTransientConnectionException` ou tiver "timeout" no nome da classe, senão
    `unavailable`), `error.type` (classe) e `error.message` (`LogSanitizer`), sem pilha.
  - **Novo `HttpMessageNotReadableException`:** 400 com `message = "Malformed request body"`,
    log `WARN` `error.reason=malformed_body`.
  - **Novo `@ExceptionHandler(Exception::class)`:**
    - se `ex is org.springframework.web.ErrorResponse`, responder com `ex.statusCode` e
      `message = HttpStatus.resolve(...)?.reasonPhrase`, log `WARN`;
    - senão, 500 com a mensagem genérica de T023 e log
      `log.atError().setMessage("Unexpected error while handling request")
      .setCause(SanitizedException.of(ex)).addKeyValue("http.method", ...)
      .addKeyValue("http.route", ...).log()`.
  - Faz T023 e T025 passar.
- [X] T029 [US3] Rodar T024. Se algum `s3cr3t-*` vazar (por exemplo logs de DEBUG do Spring
  ou Hibernate com parâmetros), corrigir na origem: nível do logger em `application.yaml` ou
  remoção do log. Nunca relaxar o teste.

**Checkpoint**: US3 completa. Quickstart §6 e §7 passam.

---

## Phase 6: User Story 4 - Pesquisa de Logs no Ambiente Local (Priority: P3)

**Goal**: Logs (e traces/métricas) exportados via OTLP para o `grafana/otel-lgtm` e
pesquisáveis no Grafana/Loki. Coletor fora do ar não afeta a API.

**Independent Test**: `docker compose up -d` + `./gradlew bootRun`. Uma chamada com
`X-Request-Id` aparece no Grafana (Explore → Loki) com
`{service_name="demo-ia-spec"} | request_id="<id>"`. Com `lgtm` parado, a API responde
normalmente.

### Tests for User Story 4 ⚠️ (escrever primeiro; devem FALHAR)

- [X] T030 [P] [US4] Escrever `src/test/kotlin/com/spec/demo_ia_spec/integration/LgtmLogExportIntegrationTest.kt`
  (`@Tag("e2e")`, `RANDOM_PORT`, Testcontainers Postgres + `LgtmStackContainer` com
  `@ServiceConnection`, mesma tag de imagem de T002 numa constante em
  `TestcontainersConfiguration`; o perfil reativa a exportação via
  `@SpringBootTest(properties=...)`):
  - fazer `GET /api/tasks` com `X-Request-Id: e2e-<uuid>`;
  - consultar a API do Loki
    (`GET {lgtm.lokiUrl}/loki/api/v1/query_range?query={service_name="demo-ia-spec"} |
    request_id="e2e-<uuid>"`) com polling de até 30 s;
  - esperar ≥ 1 resultado contendo `HTTP request completed` e o atributo `trace_id`.
- [X] T031 [P] [US4] Escrever `src/test/kotlin/com/spec/demo_ia_spec/integration/TelemetryExporterDownIntegrationTest.kt`
  (`RANDOM_PORT`, Testcontainers Postgres, exportação OTLP **ligada** apontando para uma porta
  local fechada, ex.: `http://localhost:1/v1/...`, para logs, traces e métricas):
  - 20 `GET /api/tasks` sequenciais respondem 200, cada um em < 1 s;
  - o console continua emitindo as linhas `HTTP request completed` (FR-015, SC-007).
- [X] T032 [P] [US4] Escrever `src/test/kotlin/com/spec/demo_ia_spec/observability/OpenTelemetryAppenderInstallerTest.kt`:
  - com `OpenTelemetrySdk` em memória (`InMemoryLogRecordExporter` de
    `io.opentelemetry:opentelemetry-sdk-testing`; adicionar a `testImplementation` se
    necessário), o instalador chama `OpenTelemetryAppender.install`;
  - um `log.atInfo().addKeyValue("k","v")` com `MDC request_id=x` gera um `LogRecordData` com
    atributos `request_id=x` e `k=v`.

### Implementation for User Story 4

- [X] T033 [US4] Criar `src/main/resources/logback-spring.xml`:
  - incluir `org/springframework/boot/logging/logback/defaults.xml` e `console-appender.xml`
    (mantém o console estruturado de T007);
  - declarar o appender `OTEL` (`io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender`)
    com `captureMdcAttributes=*`, `captureKeyValuePairAttributes=true`,
    `captureExperimentalAttributes=true` e `captureCodeAttributes=false`;
  - `root` com `CONSOLE` e `OTEL`, nível `${LOG_LEVEL:-INFO}`.
  - Rodar T006 de novo para garantir que o console continua JSON.
- [X] T034 [US4] Criar `src/main/kotlin/com/spec/demo_ia_spec/observability/OpenTelemetryAppenderInstaller.kt`
  (`@Component`, `InitializingBean`, recebe `OpenTelemetry`): `afterPropertiesSet()` chama
  `OpenTelemetryAppender.install(openTelemetry)`. Faz T032 passar.
- [X] T035 [US4] Configurar em `src/main/resources/application.yaml` a resiliência do exportador
  (Princípio VII, research.md §10), conferindo os nomes na documentação do Boot 4.1:
  - logs e traces: timeout de export 5 s e conexão 2 s;
  - métricas: `step: 15s`, `connect-timeout: 2s`, `read-timeout: 5s`;
  - todos externalizados com placeholders `${...:default}`.
  - Se o Boot não expuser o timeout de logs, criar um bean customizador do
    `OtlpHttpLogRecordExporter` em
    `src/main/kotlin/com/spec/demo_ia_spec/observability/OtlpExporterTimeoutsConfig.kt`.
  - Faz T031 passar.
- [X] T036 [US4] Configurar os spans JDBC em `src/main/resources/application.yaml`
  (datasource-micrometer):
  - `jdbc.datasource-proxy.include-parameter-values: false` e
    `jdbc.datasource-proxy.query.enable-logging: false` (sem parâmetros e sem log de SQL;
    FR-011/012; conferir nomes);
  - validar que `StructuredLoggingIntegrationTest` (T015) e `SensitiveDataLoggingIntegrationTest`
    (T024) continuam verdes.
- [X] T037 [US4] Validar e2e: `./gradlew test -Pe2e --tests '*LgtmLogExportIntegrationTest'`
  verde (T030). Registrar em research.md os nomes de propriedades e a tag da imagem efetivamente
  usados, trocando cada "confirmar na implementação" pelo valor confirmado.

**Checkpoint**: US4 completa. Quickstart §1, §2, §5 e §8 passam.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T038 [P] Atualizar `README.md` (em português) com a seção "Observabilidade local":
  - `docker compose up -d` sobe Postgres + Grafana;
  - Grafana em http://localhost:3000;
  - consultas LogQL de [contracts/log-record.md](./contracts/log-record.md);
  - cabeçalho `X-Request-Id`;
  - variáveis `LOG_LEVEL` e `TRACING_SAMPLING_PROBABILITY`.
- [X] T039 [P] Revisar todo log existente em `src/main/kotlin/**` (`grep -rn "log\.\|logger"`):
  nenhum concatena strings nem interpola valores de usuário; trocar por mensagem fixa + KV
  (FR-001).
- [X] T040 Rodar `./gradlew test` (suíte padrão) e `./gradlew test -Pe2e`, ambos verdes.
- [X] T041 Executar [quickstart.md](./quickstart.md) §1–§8 manualmente e marcar o resultado de
  cada passo no PR.
- [X] T042 Revisão de conformidade (Workflow da constituição): conferir que plan.md descreve
  logs, métricas, spans e dependências com timeouts, e que o desvio do Princípio VI está
  registrado em Complexity Tracking. Atualizar plan.md se a implementação divergiu.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências. T004 depende de T001 e T003.
- **Foundational (Phase 2)**: depende do Setup e bloqueia todas as histórias.
- **US1 (Phase 3)**: depende da Foundational.
- **US2 (Phase 4)**: depende de US1 (estende o `RequestIdFilter` de T011).
- **US3 (Phase 5)**: depende de US1 (T013: `requestId` no `GlobalExceptionHandler`). Pode
  rodar em paralelo com US2.
- **US4 (Phase 6)**: depende da Foundational. O teste e2e (T030) usa o log de acesso de US2
  (T018), então fazer depois de US2.
- **Polish (Phase 7)**: depende de todas as histórias.

### Within Each User Story

- Testes primeiro: devem FALHAR antes da implementação (Princípio II).
- `RequestIdFilter.kt` é alterado em T011 → T018 → T019/T020, em sequência.
- `GlobalExceptionHandler.kt` é alterado em T013 → T028, em sequência.
- `application.yaml` é alterado em T007 → T035 → T036, em sequência.

### Parallel Opportunities

- Setup: T002 e T003 em paralelo com T001.
- US1: T008, T009 e T010 em paralelo.
- US2: T015, T016 e T017 em paralelo.
- US3: T021–T025 em paralelo; T026 em paralelo com outras implementações.
- US4: T030, T031 e T032 em paralelo.
- Com duas pessoas, depois de US1: uma faz US2 enquanto a outra faz US3.
- Polish: T038 e T039 em paralelo.

---

## Parallel Example: User Story 1

```bash
Task: "RequestIdFilterTest em src/test/kotlin/com/spec/demo_ia_spec/observability/RequestIdFilterTest.kt"
Task: "Ampliar GlobalExceptionHandlerTest em src/test/kotlin/com/spec/demo_ia_spec/exception/GlobalExceptionHandlerTest.kt"
Task: "RequestIdHeaderIntegrationTest em src/test/kotlin/com/spec/demo_ia_spec/integration/RequestIdHeaderIntegrationTest.kt"
```

## Parallel Example: User Story 3

```bash
Task: "LogSanitizerTest em src/test/kotlin/com/spec/demo_ia_spec/observability/LogSanitizerTest.kt"
Task: "SanitizedExceptionTest em src/test/kotlin/com/spec/demo_ia_spec/observability/SanitizedExceptionTest.kt"
Task: "SensitiveDataLoggingIntegrationTest em src/test/kotlin/com/spec/demo_ia_spec/integration/SensitiveDataLoggingIntegrationTest.kt"
Task: "UnexpectedErrorResponseIntegrationTest em src/test/kotlin/com/spec/demo_ia_spec/integration/UnexpectedErrorResponseIntegrationTest.kt"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Phase 1 (Setup) + Phase 2 (Foundational).
2. Phase 3 (US1): **validar** quickstart §3. Já entrega o identificador ao cliente.
3. Phase 4 (US2): **validar** quickstart §4. Com isso o suporte já consegue filtrar os logs
   pelo identificador (MVP).

### Incremental Delivery

1. Setup + Foundational: logs em JSON.
2. US1: `X-Request-Id` em todas as respostas.
3. US2: logs correlacionados e log de acesso (MVP).
4. US3: erros com contexto e sanitização.
5. US4: exportação OTLP e pesquisa no Grafana/Loki.

Cada passo mantém a suíte verde e o comportamento da API de tarefas inalterado (FR-019).

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- Commit após cada tarefa ou grupo lógico, referenciando o ID (ex.: `T011`).
- Nunca relaxar um teste de dados sensíveis para fazê-lo passar. Corrigir a origem do
  vazamento.
- Nomes de propriedades marcados "confirmar" em research.md devem ser verificados na
  documentação do Boot 4.1.2 antes de usar.
