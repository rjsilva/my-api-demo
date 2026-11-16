# Pesquisa Técnica: Persistência Durável de Tarefas

**Data**: 2026-11-15
**Spec**: [spec.md](./spec.md) | **Plano**: [plan.md](./plan.md)

Este documento registra as decisões técnicas para implementar a spec, a partir das escolhas
de stack informadas pelo usuário (PostgreSQL via Docker Compose, Spring Data JPA, Flyway,
`spring-boot-docker-compose`, Testcontainers, Actuator) e das restrições da constituição
v2.0.0 (Kotlin + Spring Boot 4 + Gradle, camadas, TDD).

> **Nota sobre Spring Boot 4**: o Boot 4 modularizou a auto-configuração. Vários recursos que
> antes vinham "de graça" agora exigem starters próprios (ex.: `spring-boot-starter-flyway`) e
> alguns pacotes de teste mudaram de lugar. Os coordenados abaixo seguem o BOM do Boot 4.1.x e
> devem ser confirmados na primeira tarefa de implementação (build verde antes de prosseguir).

## 1. Banco de dados e execução local

**Decision**: PostgreSQL 18 (imagem oficial `postgres:18`), definido em `compose.yaml` na raiz,
com volume nomeado (`tasks-db-data`) montado em `/var/lib/postgresql`, porta `5432:5432` e
`healthcheck` com `pg_isready`.

**Rationale**: Atende FR-011 (um comando: `docker compose up -d`) e FR-012 (volume nomeado
sobrevive a `stop`/`down`; só `down -v` apaga). A partir do Postgres 18 a imagem oficial guarda
os dados em `/var/lib/postgresql/18/docker` e recomenda montar o volume em
`/var/lib/postgresql` (montar em `/var/lib/postgresql/data`, como em versões antigas, faz o
container falhar ou perder dados). O `healthcheck` permite que o suporte a Docker Compose do
Boot espere o banco ficar pronto antes de seguir.

**Alternatives considered**:
- Postgres instalado localmente — rejeitado: viola "sem passos manuais" (FR-011).
- Porta aleatória no host — rejeitada: impediria rodar a aplicação fora do `bootRun` (ex.:
  `java -jar`) com a configuração padrão; o custo é conflito se já existir um Postgres local na
  5432, documentado no quickstart.

## 2. Subida automática do banco no `bootRun`

**Decision**: `developmentOnly("org.springframework.boot:spring-boot-docker-compose")`, com o
ciclo de vida padrão (`start-and-stop`: sobe no início, executa `docker compose stop` ao
encerrar — o volume é preservado).

**Rationale**: Com isso `./gradlew bootRun` sozinho já sobe o banco e injeta a conexão
(`ServiceConnection`), sem configuração manual. `developmentOnly` garante que a dependência
não entra no jar de produção. O suporte é ignorado em testes por padrão
(`spring.docker.compose.skip.in-tests=true`), então não conflita com o Testcontainers.

**Alternatives considered**: Exigir sempre `docker compose up -d` antes do `bootRun` — mantido
como alternativa documentada, mas não obrigatório.

## 3. Camada de persistência

**Decision**: Spring Data JPA (`spring-boot-starter-data-jpa`) + driver
`org.postgresql:postgresql` (`runtimeOnly`). A interface de domínio `TaskRepository` existente
**é mantida**; uma nova implementação `JpaTaskRepository` (adapter, `@Repository`) delega para
um `TaskJpaRepository : JpaRepository<TaskEntity, Long>` e converte `TaskEntity` ↔ `Task`.
`InMemoryTaskRepository` e seu teste são removidos (FR-015).

**Rationale**: Manter a interface de domínio deixa `TaskService` e `TaskController`
praticamente intactos (FR-007) e mantém o JPA confinado à camada de repository (Princípio I). A
entidade JPA (`TaskEntity`, classe mutável com `var`) fica separada do `Task` de domínio
(`data class` imutável), evitando os problemas conhecidos de `data class` como entidade JPA
(`equals`/`hashCode` e proxies).

**Alternatives considered**:
- Fazer o próprio `TaskRepository` estender `JpaRepository` e anotar `Task` com `@Entity` —
  rejeitado: mistura modelo de domínio com JPA e obriga a mudar a assinatura usada pelo
  service.
- Spring Data JDBC / `JdbcClient` — mais simples, mas o usuário escolheu JPA explicitamente.

**Plugins Kotlin**: adicionar `kotlin("plugin.jpa")` (gera construtor sem argumentos para
`@Entity`) e bloco `allOpen` para `jakarta.persistence.Entity`, `MappedSuperclass` e
`Embeddable` (o `plugin.spring` não abre classes JPA).

**Configuração JPA**: `spring.jpa.hibernate.ddl-auto=validate` (o Flyway é dono do schema; o
Hibernate só confere se a entidade bate com a tabela) e `spring.jpa.open-in-view=false`.

## 4. Migrações de schema

**Decision**: Flyway via `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`.
Scripts SQL em `src/main/resources/db/migration`, começando por
`V1__create_tasks_table.sql`.

**Rationale**: Atende FR-013 (histórico versionado, no repositório, aplicado em ordem e
automaticamente na inicialização, uma vez por mudança). No Boot 4 a auto-configuração do
Flyway só é ativada com o starter dedicado; o módulo `flyway-database-postgresql` é exigido
pelo Flyway 10+ para Postgres.

**Alternatives considered**: Liquibase — equivalente, mas o usuário escolheu Flyway;
`ddl-auto=update` — rejeitado na clarificação (sem histórico).

## 5. Identificador e ordenação

**Decision**: `id BIGINT GENERATED ALWAYS AS IDENTITY` (JPA `GenerationType.IDENTITY`).
Listagem via método derivado `findAllByOrderByCompletedAscCreatedAtAscIdAsc()`.

**Rationale**: Sequências do Postgres nunca devolvem um valor já entregue, nem após exclusões
ou reinícios (FR-006); lacunas na numeração são aceitáveis. O `id` como desempate final torna a
ordem determinística quando duas tarefas têm o mesmo `createdAt` (caso de borda da spec).

## 6. Precisão de `createdAt`

**Decision**: Coluna `TIMESTAMPTZ`; o service passa a gerar `createdAt` com
`Instant.now().truncatedTo(ChronoUnit.MICROS)`.

**Rationale**: O Postgres guarda microssegundos; a JVM 21 no macOS/Linux gera `Instant` com
nanossegundos. Sem truncar, a tarefa devolvida na criação teria um `createdAt` diferente da
mesma tarefa lida após o reinício, violando FR-004/SC-001.

## 7. Atomicidade das operações

**Decision**: O adapter `JpaTaskRepository` é anotado com `@Transactional` (leituras com
`readOnly = true`). `deleteById` passa a ser uma única instrução
(`@Modifying(clearAutomatically = true, flushAutomatically = true)` `DELETE ... WHERE id = :id`
retornando a contagem, para que o cache do JPA não fique com a entidade excluída nem com
escritas pendentes), e o service usa o retorno
booleano para lançar `TaskNotFoundException`, em vez de `findById` + `deleteById`.

**Rationale**: Cada operação de escrita vira uma única transação no banco (FR-008): ou é
aplicada por inteiro, ou não tem efeito. Remover o `findById` prévio no delete elimina uma
janela de corrida e uma ida ao banco, sem mudar o comportamento externo (204 / 404). O
`@Transactional` fica na camada de repository, mantendo o service livre de preocupações de
persistência (Princípio I).

## 8. Banco indisponível durante uma operação → 503

**Decision**: `GlobalExceptionHandler` ganha um handler para
`DataAccessResourceFailureException` (inclui `CannotGetJdbcConnectionException`) e
`CannotCreateTransactionException`, respondendo `503` com o corpo já existente
`ErrorResponse(message = "Storage temporarily unavailable. Please try again later.")`.
`spring.datasource.hikari.connection-timeout=5s`.

**Rationale**: Atende FR-009 com o mesmo formato de erro (FR-007) e mensagem genérica (sem
detalhes internos). Com o JPA, a falha de conexão costuma surgir como
`CannotCreateTransactionException` ao abrir a transação. Reduzir o timeout do pool (padrão 30
s) evita que o cliente espere meio minuto para receber o 503.

**Alternatives considered**: Capturar `DataAccessException` inteira — rejeitado: erros de
programação/constraint virariam 503 ("tente de novo") em vez de 500.

## 9. Espera de até 30 s na inicialização

**Decision**: `spring.datasource.hikari.initialization-fail-timeout=30000` (ms). O Flyway é o
primeiro a pedir conexão; o Hikari tenta obter a conexão inicial por até 30 s e, se não
conseguir, lança exceção e a inicialização do Spring é abortada com a causa ("Connection to
localhost:5432 refused") nos logs.

**Rationale**: Atende FR-010 com uma única propriedade, sem código. Como a aplicação só passa
a aceitar requisições depois que o contexto sobe, nenhuma requisição é atendida durante a
espera. A mensagem padrão do Spring Boot ("Application run failed" + causa de conexão) é
considerada clara o suficiente; um `FailureAnalyzer` customizado fica como melhoria opcional.

**Alternatives considered**: `spring.flyway.connect-retries` — funciona, mas usa backoff
exponencial, o que torna o "até 30 s" aproximado; laço de retry manual — código
desnecessário.

## 10. Testes de integração

**Decision**: Testcontainers 2.x via `spring-boot-testcontainers`,
`org.testcontainers:testcontainers-junit-jupiter` e
`org.testcontainers:testcontainers-postgresql` (versões do BOM do Boot). Uma classe
`TestcontainersConfiguration` (`@TestConfiguration(proxyBeanMethods = false)`) expõe
`@Bean @ServiceConnection PostgreSQLContainer("postgres:18")` — a mesma imagem do
`compose.yaml` (FR-016). Os testes de integração importam essa configuração; o cache de
contexto do Spring reaproveita um único container por execução.

**Rationale**: Mesmo tipo e versão de banco da aplicação, criado e descartado automaticamente,
sem depender do banco de desenvolvimento (FR-016). Requer Docker em execução para rodar os
testes (pré-requisito documentado).

**Estratégia de testes por camada (TDD — Princípio II)**:

| Camada | Tipo | Banco |
|---|---|---|
| `JpaTaskRepository` | `@DataJpaTest` + Testcontainers: salvar, ordenação com desempate, `deleteById` booleano, ids não reutilizados, round-trip de `createdAt`, acentos/descrição longa | Postgres real |
| `TaskService` | Unitário com `TaskRepository` mockado (Mockito) — substitui o uso do `InMemoryTaskRepository` | Nenhum |
| `TaskController` / `GlobalExceptionHandler` | `@WebMvcTest` (existente) + novo caso: service lança `CannotCreateTransactionException` → 503 com `{ "message": ... }` | Nenhum |
| Aplicação inteira | `@SpringBootTest` + Testcontainers: cenários da 001 via HTTP (regressão, SC-002) e health com `db` | Postgres real |
| Reinício (US1/SC-001/SC-004) | Teste que sobe um contexto com `SpringApplicationBuilder`, cria/conclui/exclui tarefas, fecha o contexto, sobe outro contexto contra **o mesmo container** e confere a listagem | Postgres real |

Isolamento entre testes: limpar a tabela `tasks` antes de cada teste de integração
(`TRUNCATE`), sem reiniciar a identidade, para que ids nunca se repitam também nos testes.

**Notas Boot 4**: `@DataJpaTest` mudou para o módulo de teste do Data JPA
(`spring-boot-data-jpa-test`, pacote `org.springframework.boot.data.jpa.test.autoconfigure`);
usar `@AutoConfigureTestDatabase(replace = NONE)` explicitamente para não trocar o Postgres por
um banco embutido. Com Mockito puro em Kotlin, `any()` retorna `null` em parâmetros não
anuláveis — preferir stubs com valores concretos.

## 11. Observabilidade

**Decision**: `spring-boot-starter-actuator`, expondo apenas o endpoint `health` via HTTP
(`management.endpoints.web.exposure.include=health`) com
`management.endpoint.health.show-components=always` (mostra `db: UP/DOWN` sem expor
detalhes como versão do banco). Logs: padrão do Spring Boot (console, nível `INFO`), sem
configuração adicional.

**Rationale**: O indicador de saúde do `DataSource` é ativado automaticamente com JPA; o
`/actuator/health` responde `503` com `status: DOWN` quando o banco cai, coerente com o FR-009.
`show-components` sem `show-details` evita vazar informações internas (mesmo princípio das
mensagens de erro genéricas).

**Alternatives considered**: `show-details=always` — expõe nome/versão do banco sem
necessidade; métricas/Prometheus/tracing — fora do escopo pedido.
