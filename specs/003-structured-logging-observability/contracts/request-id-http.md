# Contrato HTTP: Identificador de Requisição

Vale para **todas** as rotas da aplicação (`/api/tasks/**`, `/actuator/health` e rotas
inexistentes).

## Requisição

| Cabeçalho | Obrigatório | Regra |
|---|---|---|
| `X-Request-Id` | Não | Reaproveitado se casar com `^[A-Za-z0-9._-]{1,128}$`. Caso contrário, é ignorado e um UUID v4 é gerado. Nunca causa erro |
| `traceparent` | Não | W3C Trace Context. Se válido, o trace continua a partir dele; se inválido, um novo trace é iniciado |

## Resposta

| Cabeçalho | Sempre presente | Valor |
|---|---|---|
| `X-Request-Id` | Sim, em qualquer status (2xx, 4xx, 5xx) | O `request_id` efetivo da requisição |

## Corpo de erro (4xx/5xx gerados pela API)

Formato existente, com um campo novo (aditivo):

```json
{
  "message": "Task not found: 42",
  "requestId": "3f2c9a1e-8b7d-4e21-9c55-0a6f1d2b7e90"
}
```

| Status | `message` | Observação |
|---|---|---|
| 400 | Lista de `campo: motivo` (inalterado) | |
| 404 | `Task not found: {id}` (inalterado) | |
| 503 | `Storage temporarily unavailable. Please try again later.` (inalterado) | |
| 500 | `Unexpected error. Please contact support with the request id.` (**novo**) | Sem pilha, nome de classe ou detalhe interno (FR-013) |

As respostas de sucesso (201, 200, 204) **não mudam de corpo**; só ganham o cabeçalho.

## Exemplos

```http
POST /api/tasks HTTP/1.1
X-Request-Id: suporte-ticket-1234
Content-Type: application/json

{"title":"Comprar pão"}
```
```http
HTTP/1.1 201 Created
X-Request-Id: suporte-ticket-1234
```

```http
GET /api/tasks HTTP/1.1
X-Request-Id: valor com espaço e \r\n
```
```http
HTTP/1.1 200 OK
X-Request-Id: 7b1e0c3a-2f44-4d1b-a7f2-5c9e8d6b1a03
```
