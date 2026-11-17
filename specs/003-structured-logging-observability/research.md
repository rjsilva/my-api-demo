# Pesquisa (Fase 0): Logs Estruturados e Rastreáveis por Requisição

**Funcionalidade**: [spec.md](./spec.md) | **Plano**: [plan.md](./plan.md) | **Data**: 2026-11-16

Cada seção registra **Decisão**, **Justificativa** e **Alternativas consideradas**. Os nomes de
propriedades e versões que estavam "a confirmar" foram verificados na implementação contra os
metadados do Spring Boot 4.1.2 e estão consolidados na §13.

---

## 1. Formato dos logs no console

**Decisão**: Usar o suporte nativo do Spring Boot com `logging.structured.format.console: ecs`
(Elastic Common Schema). Padronizar os nomes dos campos de rastreamento com
`logging.structured.json.rename` (`traceId` → `trace_id`, `spanId` → `span_id`), de modo que
fiquem iguais a `request_id`.

**Justificativa**:
- É o recurso pedido pelo usuário e não precisa de biblioteca extra (Logback + encoder nativo do
  Boot).
- O ECS já traz `@timestamp`, `log.level`, `message`, `log.logger`, `process.thread.name`,
  `service.name` (vindo de `spring.application.name`) e, para exceções, `error.type`,
  `error.message` e `error.stack_trace`. Isso cobre FR-002 e FR-008.
- O Boot inclui automaticamente as entradas do MDC e os pares chave-valor do SLF4J 2
  (`log.atInfo().addKeyValue(...)`) como campos de primeiro nível. Isso atende FR-001 sem
  concatenar strings.

**Alternativas consideradas**:
- `logstash`: equivalente. O ECS foi escolhido porque os nomes dos campos de erro e serviço
  seguem um padrão documentado.
- `gelf`: voltado ao Graylog, sem ganho aqui.
- `logstash-logback-encoder`: dependência extra, desnecessária desde o Boot 3.4.
- Arquivo de log: fora de escopo. Console + OTLP bastam (§4).

## 2. Identificador de requisição (`X-Request-Id`)

**Decisão**: Um `RequestIdFilter` (`OncePerRequestFilter`) registrado com
`Ordered.HIGHEST_PRECEDENCE`:
1. Lê o cabeçalho `X-Request-Id`. O valor é aceito se casar com `^[A-Za-z0-9._-]{1,128}$`.
   Caso contrário (ausente, vazio, longo demais, caracteres inválidos), gera
   `UUID.randomUUID()`.
2. Coloca o valor no MDC com a chave `request_id`.
3. Escreve `X-Request-Id` na resposta **antes** de chamar a cadeia de filtros, para que o
   cabeçalho saia mesmo em erros e respostas já confirmadas.
4. Guarda o valor como atributo da requisição (`RequestIdFilter.ATTRIBUTE`), para que o
   `GlobalExceptionHandler` o inclua no corpo de erro.
5. Adiciona `request.id` como atributo do span HTTP atual (via `Tracer` do Micrometer), para que
   o trace também seja pesquisável pelo identificador.
6. Remove a chave do MDC em `finally`, para não vazar valores entre requisições na mesma thread
   (SC-002).

**Justificativa**: É o desenho pedido pelo usuário. O whitelist de caracteres e tamanho impede
injeção de log/cabeçalho (CR/LF) e valores abusivos. Escrever o cabeçalho antes da cadeia garante
FR-005 também para 404 de rota inexistente e para 500.

**Alternativas consideradas**:
- Usar o `trace_id` W3C como identificador devolvido: era a premissa inicial da spec, mas foi
  trocada pela escolha explícita do usuário. O `traceparent` continua sendo propagado (§3), e o
  `trace_id` aparece em todo log, então os dois coexistem (ver Complexity Tracking no plano).
- Aceitar qualquer valor do cliente: rejeitado por risco de injeção.
- Rejeitar com 400 um `X-Request-Id` inválido: rejeitado, porque a spec exige que a requisição
  seja atendida normalmente.

## 3. Rastreamento distribuído (`traceparent`)

**Decisão**: Adicionar `spring-boot-starter-opentelemetry` (Boot 4). Ele traz o Micrometer
Tracing com bridge OpenTelemetry, exportadores OTLP e a propagação W3C (`traceparent`) como
padrão. O `ServerHttpObservationFilter` cria o span da requisição e põe `traceId`/`spanId` no
MDC. Em desenvolvimento, `management.tracing.sampling.probability: 1.0`.

Para cumprir o Princípio VI ("toda chamada de saída... banco"), as chamadas JDBC passam a gerar
spans via `net.ttddyy.observation:datasource-micrometer-spring-boot`. A versão é declarada no
`build.gradle.kts`, pois não é gerida pelo BOM do Boot (confirmado: 2.3.0, construída sobre o
Boot 4.1). O `DataSourceNameResolver` padrão da biblioteca abre uma conexão para ler o nome do
catálogo; com o banco fora do ar isso dobrava a espera de 30 s na inicialização (regressão do
FR-010 da 002). Um bean próprio (`JdbcObservationConfig`) usa o nome do bean, sem conectar. A captura de parâmetros de query fica desligada para não registrar
dados de usuário (FR-011/FR-012).

**Justificativa**: A constituição exige OpenTelemetry/OTLP e tracing de entrada e saída. O
starter oficial do Boot é a integração recomendada pelas Restrições Técnicas.

**Alternativas consideradas**:
- Java agent do OpenTelemetry: instrumentação automática mais ampla, mas muda a forma de
  executar a aplicação e duplica o que o Boot já faz. Rejeitado.
- Deixar JDBC sem spans: viola o Princípio VI.

## 4. Exportação dos logs via OTLP

**Decisão**: Usar o `OpenTelemetryAppender` (`io.opentelemetry.instrumentation:
opentelemetry-logback-appender-1.0`, versão via `opentelemetry-instrumentation-bom` declarado
no `build.gradle.kts`). Ele fica num `logback-spring.xml` que inclui a configuração padrão do Boot
(console estruturado) e acrescenta esse appender. Um bean `InstallOpenTelemetryAppender`
(`InitializingBean`) chama `OpenTelemetryAppender.install(openTelemetry)` com o `OpenTelemetry`
auto-configurado pelo Boot, que já tem o exportador OTLP de logs. Opções do appender:
- `captureMdcAttributes=*`: exporta `request_id`.
- `captureKeyValuePairAttributes=true`: exporta método, rota, status e duração do log de acesso.
- `captureExperimentalAttributes=true`: nome da thread.
- `captureCodeAttributes=false`.

O endpoint vem de `management.opentelemetry.logging.export.otlp.endpoint` (confirmado no Boot
4.1.2). O appender é um artefato *alpha*: a versão vem de
`opentelemetry-instrumentation-bom-alpha` (2.28.1-alpha, que importa o BOM estável 2.28.1 e fixa
o SDK OTel 1.62.0, o mesmo gerido pelo Boot 4.1.2).

O `console-appender.xml` do Boot é baseado em *pattern*, então o `logback-spring.xml` declara o
appender `CONSOLE` com o `StructuredLogEncoder` do Boot
(`${CONSOLE_LOG_STRUCTURED_FORMAT:-ecs}`) em vez de incluí-lo. Em dev, o service connection do `spring-boot-docker-compose`
para a imagem `grafana/otel-lgtm` preenche esse endpoint automaticamente, junto com os de traces
e métricas (FR-017).

**Justificativa**: É o caminho documentado pelo Spring para logs OTLP. O exportador usa
`BatchLogRecordProcessor`, que é assíncrono e tem fila limitada, então um coletor lento ou fora
do ar não bloqueia requisições (FR-015). O console estruturado continua sendo a fonte primária.

**Alternativas consideradas**:
- Coletar o stdout com Promtail/Alloy e enviar ao Loki: exige outro contêiner e configuração de
  scraping de Docker. O usuário pediu OTLP. Rejeitado.
- Appender HTTP direto para o Loki (`loki-logback-appender`): específico de fornecedor, viola as
  Restrições Técnicas.

## 5. Ambiente local: `grafana/otel-lgtm` no `compose.yaml`

**Decisão**: Novo serviço `lgtm` no `compose.yaml` existente:
- imagem `grafana/otel-lgtm` com tag fixa (a estável mais recente no momento da implementação,
  nunca `latest`);
- portas `3000:3000` (Grafana), `4317:4317` (OTLP gRPC) e `4318:4318` (OTLP HTTP);
- healthcheck no endpoint de saúde do Grafana.

O comando único continua sendo `docker compose up -d` (ou só `./gradlew bootRun`, que sobe o
compose pelo `spring-boot-docker-compose`). O exportador usa OTLP HTTP (4318). A porta 4317 fica
exposta para outras ferramentas.

**Justificativa**: Imagem recomendada pela constituição. Loki, Tempo, Prometheus/Mimir e Grafana
já vêm com data sources configurados, então não há dashboard ou provisionamento a manter.

**Alternativas consideradas**: Loki, Tempo e Grafana em contêineres separados. Dá mais trabalho
de configuração sem benefício local.

## 6. Pesquisa no Grafana (Loki)

**Decisão**: Documentar no quickstart as consultas LogQL na tela Explore → Loki:
- todos os logs da aplicação: `{service_name="demo-ia-spec"}`;
- logs de uma requisição: `{service_name="demo-ia-spec"} | request_id="<valor>"`;
- erros: `{service_name="demo-ia-spec"} | severity_text="ERROR"`.

Os atributos OTLP chegam como *structured metadata* no Loki. O `trace_id` de cada log liga ao
trace no Tempo pelo data source pré-configurado.

**Justificativa**: Atende US4 e SC-003 sem configuração extra.

**Alternativas consideradas**: Dashboard provisionado. Fica para depois; o Explore basta para o
fluxo de suporte.

## 7. Registro de erros e níveis de severidade

**Decisão**:
- `GlobalExceptionHandler` ganha um handler genérico `@ExceptionHandler(Exception::class)` que
  devolve `500` com mensagem genérica e loga em `ERROR` com a exceção (pilha completa).
- Erros de negócio (`TaskNotFoundException`, `MethodArgumentNotValidException`) logam em `WARN`
  com o motivo e sem pilha. Na validação, o log traz só os **nomes** dos campos rejeitados, nunca
  os valores.
- Banco indisponível (`503`) loga em `ERROR` com `dependency=database` e
  `failure=unavailable|timeout`, sem pilha e com mensagem sanitizada (§8).
- O log de acesso (§9) registra toda requisição.
- `ErrorResponse` ganha o campo `requestId`. A mudança é aditiva: o campo `message` continua igual
  (FR-006, FR-019).

**Justificativa**: Atende FR-008/009/010/013. Hoje não há handler genérico, e uma exceção não
tratada cai no `/error` do Boot, que é logado pelo Tomcat sem o nosso formato de corpo.

**Alternativas consideradas**: `ProblemDetail` (RFC 9457). Mudaria o formato do corpo de erro
existente (regressão da 001/002). Rejeitado.

## 8. Proteção de dados sensíveis

**Decisão**: Defesa em camadas:
1. **Não logar na origem**: nenhum código loga corpos, cabeçalhos ou valores de campos. O log de
   acesso tem uma lista fechada de campos (§9).
2. **`LogSanitizer`**: utilitário que mascara padrões sensíveis em textos livres antes de logar:
   `password=...`, `pwd=...`, `token=...`, `secret=...`, `Authorization: ...`, `Bearer ...` e
   credenciais em URLs `scheme://user:pass@host`, trocando o valor por `***`. É usado em toda
   mensagem de exceção registrada pelo `GlobalExceptionHandler`. Para erros inesperados, a exceção
   vai para o log embrulhada numa `SanitizedException`, que copia a pilha original e traz mensagens
   sanitizadas (incluindo as *causes*). Assim o mesmo conteúdo protegido sai no console **e** no
   OTLP, que são dois appenders diferentes.
3. **Telemetria**: sem parâmetros de query nos spans JDBC (§3); sem cabeçalhos ou corpo como
   atributos de span; tags padrão de `http.server.requests` (método, rota-padrão, status), que
   usam o padrão da rota e não a URI com valores.
4. **Configuração**: `server.error.include-stacktrace: never` e
   `server.error.include-message: never` como garantia extra para o `/error` do Boot.

**Justificativa**: Mascarar só no encoder JSON não cobriria o appender OTLP. Sanitizar na
origem cobre os dois e é testável unitariamente. Atende FR-011/012/013 e SC-004.

**Alternativas consideradas**:
- `StructuredLoggingJsonMembersCustomizer` com regex: cobre só o console. Rejeitado como
  mecanismo único.
- Processador de logs no coletor: o coletor embutido no otel-lgtm não é nosso para configurar, e
  o console continuaria exposto.

## 9. Log de acesso por requisição

**Decisão**: O próprio `RequestIdFilter`, no `finally` depois da cadeia, emite um `INFO`
`"HTTP request completed"` com pares chave-valor: `http.method`, `http.route` (padrão da rota
obtido de `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, com fallback `UNMATCHED`),
`http.status_code` e `duration_ms`. A query string não é registrada.

**Justificativa**: Atende FR-007. Usar o padrão da rota (e não a URI) mantém a cardinalidade
baixa e evita registrar valores de entrada.

**Alternativas consideradas**: Access log do Tomcat. Não é estruturado no mesmo formato e não
carrega o MDC. Rejeitado.

## 10. Resiliência do exportador OTLP (Princípio VII)

**Decisão**: O coletor OTLP é uma nova dependência externa. Timeouts explícitos ficam no
`application.yaml`:
- logs e traces: timeout de exportação de 5 s e de conexão de 2 s;
- métricas: intervalo de 15 s, `connect-timeout` de 2 s e `read-timeout` de 5 s.

Os nomes das propriedades foram confirmados no Boot 4.1.2 (§13); o Boot expõe o timeout de logs,
então nenhum customizador foi necessário.

**Comportamento de falha**: descartar a telemetria daquele lote (sem retry além do padrão do
exportador). A fila do batch processor é limitada, então os lotes mais antigos são descartados
quando ela enche. A falha não chega à requisição; os logs continuam no console.

**Teste**: com o endpoint OTLP apontando para uma porta fechada, as requisições continuam
respondendo com sucesso e dentro do tempo (SC-007).

**Justificativa**: Telemetria é melhor esforço e nunca pode derrubar a API (FR-015).

**Alternativas consideradas**: Retry com backoff ou buffer em disco. Desnecessário localmente e
aumenta a complexidade.

## 11. Estratégia de testes

**Decisão**:
- **Unitários (JUnit 5)**:
  - `RequestIdFilterTest` (com `MockHttpServletRequest`/`MockHttpServletResponse`): valor válido
    reaproveitado; ausente, vazio, longo demais ou com CR/LF geram UUID; cabeçalho na resposta; MDC
    limpo depois.
  - `LogSanitizerTest`: cada padrão sensível.
  - `GlobalExceptionHandlerTest` (ampliado): `requestId` no corpo; 500 genérico sem detalhes.
- **Web (`@WebMvcTest`)**: `X-Request-Id` presente em 201/200/204/400/404/500.
- **Integração (`@SpringBootTest` + Testcontainers Postgres + `OutputCaptureExtension`)**:
  - `StructuredLoggingIntegrationTest`: cada linha capturada é JSON válido com os campos
    obrigatórios; todos os logs de uma requisição têm o mesmo `request_id` devolvido.
  - `ConcurrentRequestIdIntegrationTest`: 50 requisições paralelas sem nenhum log atribuído à
    requisição errada (SC-002).
  - `SensitiveDataLoggingIntegrationTest`: envia `Authorization`, `Cookie` e senha na
    query/corpo; nenhum aparece na saída (SC-004).
  - `TelemetryExporterDownIntegrationTest`: endpoint OTLP numa porta fechada; requisições OK
    (SC-007).
- **Integração ponta a ponta (opcional/lento, tag `e2e`)**: `LgtmLogExportIntegrationTest` com
  `LgtmStackContainer` (Testcontainers `grafana`) e `@ServiceConnection`. Faz uma chamada,
  consulta a API HTTP do Loki por `request_id` e espera encontrar o registro (FR-014/FR-017).
- **Ambiente de testes**: a exportação OTLP fica desligada no perfil de testes padrão (métricas,
  traces e logs com export desabilitado), para não tentar conectar em `localhost:4318`.

**Justificativa**: Cobre cada FR e SC automaticamente, seguindo o Princípio II. O teste e2e
valida a integração real sem tornar a suíte padrão lenta.

**Alternativas consideradas**: Validar a exportação só manualmente pelo quickstart. Fica como
complemento, não como substituto.

## 12. Métricas (Princípio VI)

**Decisão**:
- **RED**: `http.server.requests` (auto-configurada pelo Actuator) exportada via OTLP para o
  Prometheus/Mimir do otel-lgtm.
- **Métrica própria da funcionalidade**: `logback.events` (auto-configurada, contagem de logs por
  nível). Serve como taxa de erros registrados.
- **Métricas de banco**: `hikaricp.*` e `jdbc.*` (via datasource-micrometer) vêm sem esforço
  extra.
- Nenhuma tag recebe `request_id` nem valores de usuário, para manter a cardinalidade baixa.

**Justificativa**: Atende o mínimo da constituição sem criar métricas artificiais para uma
funcionalidade transversal.

**Alternativas consideradas**: Um contador customizado de "requisições com `X-Request-Id` do
cliente". Pouco valor, descartado.

## 13. Valores confirmados na implementação (Boot 4.1.2)

| Item | Valor usado |
|---|---|
| Imagem LGTM (`compose.yaml` e `TestcontainersConfiguration.LGTM_IMAGE`) | `grafana/otel-lgtm:0.40.0` |
| BOM do appender Logback | `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:2.28.1-alpha` |
| datasource-micrometer | `net.ttddyy.observation:datasource-micrometer-spring-boot:2.3.0` |
| Liga/desliga export de logs | `management.logging.export.otlp.enabled` |
| Liga/desliga export de traces | `management.tracing.export.otlp.enabled` |
| Liga/desliga export de métricas | `management.otlp.metrics.export.enabled` |
| Endpoint e timeouts de logs | `management.opentelemetry.logging.export.otlp.{endpoint,timeout,connect-timeout}` |
| Endpoint e timeouts de traces | `management.opentelemetry.tracing.export.otlp.{endpoint,timeout,connect-timeout}` |
| URL, intervalo e timeouts de métricas | `management.otlp.metrics.export.{url,step,connect-timeout,read-timeout}` |
| JDBC sem parâmetros e sem log de SQL | `jdbc.datasource-proxy.include-parameter-values: false`, `jdbc.datasource-proxy.query.enable-logging: false` |
| Ordem do `ServerHttpObservationFilter` | `HIGHEST_PRECEDENCE + 1`; o `RequestIdFilter` roda em `HIGHEST_PRECEDENCE + 2`, dentro do escopo do span, para o log de acesso ter `trace_id` |

**Formato ECS no console**: o formatter ECS do Boot 4 escreve nomes com ponto como objetos
aninhados (`{"log":{"level":"INFO"}}`, `{"http":{"route":...}}`), o que é equivalente no ECS. Os
nomes de campo do contrato (`log.level`, `http.route`) são caminhos ECS.

**Erro inesperado**: o formatter ECS e o appender OTel derivam `error.type` da classe em tempo de
execução do throwable registrado; registrar a `SanitizedException` mostraria essa classe. Por isso
o handler registra `error.type` (classe original), `error.message` e `error.stack_trace` (gerado
a partir da `SanitizedException`, com as linhas originais da pilha e mensagens sanitizadas) como
pares chave-valor, o que funciona igual no console e no OTLP.

**`TypeMismatchException`** (ex.: `PATCH /api/tasks/abc/complete`) não é um `ErrorResponse` do
Spring; ganhou handler próprio (400) para não cair no handler genérico de 500.

