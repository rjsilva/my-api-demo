# Contrato: Health Check

**Pesquisa**: [research.md §11](../research.md)

Endpoint operacional (não faz parte da API de tarefas), fornecido pelo Spring Boot Actuator.
É o único endpoint do Actuator exposto via HTTP.

## GET /actuator/health

| Status | Quando | Corpo (exemplo) |
|---|---|---|
| `200 OK` | Aplicação e banco disponíveis | ver abaixo |
| `503 Service Unavailable` | Algum componente indisponível (ex.: banco fora do ar) | mesmo formato, com `"status": "DOWN"` |

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

Garantias:

- O componente `db` está sempre presente e reflete a conectividade com o banco.
- Os componentes mostram apenas o `status` — sem `details` (versão do banco, caminhos de disco,
  etc.).
- O formato é o padrão do Actuator; os nomes de componentes além de `db` podem variar.
