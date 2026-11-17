# Quickstart: Validar Logs Estruturados e Pesquisa Local

Guia de validação ponta a ponta. Contratos em [contracts/](./contracts/), campos em
[data-model.md](./data-model.md).

## Pré-requisitos

- Docker em execução
- Java 21 (o Gradle wrapper cuida do resto)
- Portas livres: 5432 (Postgres), 3000 (Grafana), 4317/4318 (OTLP), 8080 (aplicação)

## 1. Subir o ambiente com um único comando (US4, FR-016)

```bash
docker compose up -d
docker compose ps        # postgres e lgtm devem aparecer como healthy
```

Esperado: Grafana em http://localhost:3000 (login anônimo/admin do otel-lgtm).

> Alternativa: `./gradlew bootRun` sobe o compose automaticamente e já configura a exportação
> OTLP.

## 2. Rodar a aplicação

```bash
./gradlew bootRun
```

Esperado: todas as linhas do console são JSON (US2, FR-001), por exemplo com
`"message":"Started DemoIaSpecApplication..."` e sem `request_id` (log fora de requisição).

## 3. Identificador devolvido (US1)

```bash
# Sem identificador: um UUID é gerado
curl -si -X POST localhost:8080/api/tasks -H 'Content-Type: application/json' \
  -d '{"title":"Teste de logs"}' | grep -i x-request-id

# Com identificador válido: o mesmo valor é devolvido
curl -si localhost:8080/api/tasks -H 'X-Request-Id: suporte-1234' | grep -i x-request-id

# Inválido: é substituído por um UUID
curl -si localhost:8080/api/tasks -H 'X-Request-Id: tem espaço' | grep -i x-request-id

# Erro: cabeçalho e corpo trazem o identificador
curl -si -X DELETE localhost:8080/api/tasks/999999 -H 'X-Request-Id: suporte-404'
```

Esperado (último comando): `404`, cabeçalho `X-Request-Id: suporte-404` e corpo
`{"message":"Task not found: 999999","requestId":"suporte-404"}`.

## 4. Logs correlacionados no console (US2)

Na saída da aplicação, as linhas da chamada `suporte-404` contêm `"request_id":"suporte-404"`,
o mesmo `trace_id`, um `WARN` com `error.reason` = `task_not_found` e um `INFO`
`HTTP request completed` com `http.status_code: 404`.

## 5. Pesquisar no Grafana (US4, SC-003)

1. http://localhost:3000 → **Explore** → data source **Loki**.
2. Rodar `{service_name="demo-ia-spec"} | request_id="suporte-404"`.
3. Esperado: os mesmos registros vistos no console. Pelo `trace_id`, abrir o trace no
   **Tempo** e ver o span HTTP e o span JDBC.
4. Filtrar erros: `{service_name="demo-ia-spec"} | severity_text="ERROR"`.

## 6. Dados sensíveis (US3, SC-004)

```bash
curl -s localhost:8080/api/tasks -H 'Authorization: Bearer segredo-123' \
  -H 'Cookie: session=abc' -H 'X-Request-Id: sensivel-1' > /dev/null
```

Esperado: nem `segredo-123` nem `session=abc` aparecem no console nem no Loki
(`{service_name="demo-ia-spec"} |= "segredo-123"` não retorna nada).

## 7. Banco fora do ar (US3, FR-010)

```bash
docker compose stop postgres
curl -si localhost:8080/api/tasks -H 'X-Request-Id: db-down-1'
docker compose start postgres
```

Esperado: `503` com `requestId: db-down-1`. No log, um `ERROR` com `dependency=database`, sem
senha nem URL com credenciais.

## 8. Coletor fora do ar (US4, FR-015, SC-007)

```bash
docker compose stop lgtm
curl -si localhost:8080/api/tasks    # continua 200, sem atraso perceptível
docker compose start lgtm
```

## 9. Testes automatizados

```bash
./gradlew test                       # suíte padrão (exportação OTLP desligada)
./gradlew test -Pe2e                 # inclui LgtmLogExportIntegrationTest (sobe otel-lgtm)
```
