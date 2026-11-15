# Pesquisa Técnica: Gerenciamento de Tarefas

**Data**: 2026-11-13
**Spec**: [spec.md](./spec.md)

Este documento resolve as decisões técnicas necessárias para implementar a especificação,
dado o contexto do projeto (Kotlin + Spring Boot 4, arquitetura em camadas, TDD com JUnit 5,
conforme a constituição v2.0.0).

## 1. Framework web

**Decision**: Usar `spring-boot-starter-web` para expor a API REST (controllers anotados com
`@RestController`).

**Rationale**: O `build.gradle.kts` atual só tem `spring-boot-starter` (sem suporte web). O
starter web é a forma idiomática do Spring Boot de expor endpoints HTTP e é exigido pela
Constituição (Princípio V — Spring Boot como stack obrigatória).

**Alternatives considered**: Servidor HTTP cru (Ktor, Netty direto) — rejeitado por divergir da
stack definida na constituição sem necessidade real.

## 2. Validação de entrada

**Decision**: Usar `spring-boot-starter-validation` (Jakarta Bean Validation) com anotações
`@NotBlank` e `@Size(max = 100)` no DTO de criação de tarefa.

**Rationale**: É o mecanismo padrão do Spring para validar `title` obrigatório e o limite de
100 caracteres (FR-002, FR-003), delegando a validação ao controller antes de chegar à camada
de serviço.

**Alternatives considered**: Validação manual na camada de serviço — rejeitada por gerar mais
código boilerplate sem benefício, já que o Spring já resolve isso declarativamente.

## 3. Persistência

**Decision**: Armazenamento em memória, encapsulado inteiramente na camada de repository (sem
banco de dados).

**Rationale**: A especificação não exige persistência entre reinicializações (não há requisito
ou premissa sobre isso); trata-se de uma API de demonstração. Manter o acesso em memória
isolado na camada de repository (Princípio I — Arquitetura em Camadas) permite trocar por um
banco real no futuro sem afetar controller/service.

**Alternatives considered**: H2/JPA — rejeitado por adicionar complexidade (configuração de
banco, migrações) não solicitada pela especificação (YAGNI).

## 4. Geração de identificador e ordenação

**Decision**: Cada tarefa recebe um `id` sequencial (Long, gerado pelo repository) e um
`createdAt` (Instant) definido no momento da criação. A listagem ordena por
`(completed asc, createdAt asc)`.

**Rationale**: Garante a ordenação exigida (FR-005, FR-006) de forma determinística,
independentemente da estrutura de armazenamento interna usada pelo repository.

**Alternatives considered**: Confiar na ordem de iteração de um `HashMap` — rejeitado por não
ser garantida pela linguagem/JVM.

## 5. Tratamento de erros

**Decision**: Usar um `@RestControllerAdvice` central para mapear exceções de domínio
(`TaskNotFoundException` → 404; erros de validação → 400) para respostas HTTP com corpo de
erro estruturado (ex.: `{ "message": "..." }`).

**Rationale**: Atende FR-009 e SC-004 (erro claro e identificável), seguindo o padrão idiomático
do Spring para tratamento de erros centralizado, sem duplicar lógica de erro em cada endpoint.

**Alternatives considered**: Deixar exceções não tratadas propagarem como 500 genérico —
rejeitado por violar FR-009/SC-004.

## 6. Estratégia de testes

**Decision**: JUnit 5 (via `kotlin-test-junit5`, já presente) para testes unitários de
service/repository; `@WebMvcTest` + `MockMvc` para testes de contrato/integração do
controller; `@SpringBootTest` apenas se necessário para um teste de ponta a ponta.

**Rationale**: Já são dependências presentes no projeto (`spring-boot-starter-test`); atende
ao Princípio II (Test-First, JUnit 5) sem introduzir uma nova biblioteca de teste (ex.: Kotest).

**Alternatives considered**: Kotest — rejeitado por não estar nas dependências atuais e por
introduzir uma segunda convenção de teste no projeto.

## Resumo das dependências a adicionar

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-validation`

(Ambas serão adicionadas ao `build.gradle.kts` como parte das tasks de implementação.)