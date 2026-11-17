# Especificação da Funcionalidade: Logs Estruturados e Rastreáveis por Requisição

**Branch da Funcionalidade**: `feat003/observality`

**Criado em**: 2026-11-16

**Status**: Draft

**Entrada**: Descrição do usuário: "Os logs da aplicação devem ser estruturados e pesquisáveis. Cada requisição recebe um identificador único que aparece em todos os logs gerados por ela e é devolvido ao cliente na resposta, para facilitar o suporte. Erros devem ser registrados com contexto suficiente para diagnóstico, sem expor dados sensíveis. O ambiente local deve permitir pesquisar os logs numa interface, subindo com um único comando junto com o banco."

## Cenários de Usuário e Testes *(obrigatório)*

### História de Usuário 1 - Identificador da Requisição Devolvido ao Cliente (Prioridade: P1)

Como cliente da API (ou pessoa de suporte que atende esse cliente), eu quero que toda resposta
traga um identificador único da requisição, para poder informá-lo ao suporte e localizar
exatamente o que aconteceu naquela chamada.

**Por que esta prioridade**: É o ponto de partida de todo atendimento de suporte. Sem um
identificador que o cliente consiga ver e repassar, não há como ligar uma reclamação a um
registro específico.

**Teste Independente**: Pode ser testado isoladamente fazendo chamadas a qualquer operação da
API (com sucesso e com erro) e verificando que cada resposta traz um identificador, que ele é
diferente a cada chamada e que, quando o cliente envia um identificador de rastreamento válido,
esse mesmo valor é reaproveitado.

**Cenários de Aceitação**:

1. **Dado** um cliente que não envia nenhum identificador, **Quando** ele cria uma tarefa,
   **Então** a resposta traz um identificador de requisição gerado pelo sistema.
2. **Dado** duas chamadas consecutivas e idênticas, **Quando** o cliente compara as respostas,
   **Então** cada uma traz um identificador diferente.
3. **Dado** uma chamada que resulta em erro (por exemplo, tarefa não encontrada ou dados
   inválidos), **Quando** a resposta é devolvida, **Então** ela também traz o identificador da
   requisição.
4. **Dado** um cliente que envia o seu próprio identificador de requisição válido,
   **Quando** a requisição é processada, **Então** o sistema usa esse mesmo valor nos logs e o
   devolve na resposta.
5. **Dado** um cliente que envia um identificador de requisição malformado (vazio, longo
   demais ou com caracteres não permitidos), **Quando** a requisição é processada, **Então** o
   sistema ignora o valor inválido, gera um novo identificador e a requisição é atendida
   normalmente.
6. **Dado** um cliente que envia um contexto de rastreamento válido no padrão W3C Trace
   Context, **Quando** a requisição é processada, **Então** o sistema dá continuidade a esse
   rastreamento, independentemente do identificador de requisição.

---

### História de Usuário 2 - Logs Estruturados e Correlacionados (Prioridade: P1)

Como pessoa de suporte ou desenvolvedora, eu quero que todos os logs sejam registros
estruturados (campos nomeados, não texto livre) e que cada registro gerado durante uma
requisição carregue o identificador dela, para filtrar tudo o que aconteceu numa chamada
específica.

**Por que esta prioridade**: O identificador só tem valor se ele aparecer em todos os registros
da requisição e se esses registros puderem ser filtrados por campo. Juntas, as Histórias 1 e 2
formam o MVP.

**Teste Independente**: Pode ser testado isoladamente executando uma operação, capturando a
saída de logs da aplicação e verificando que cada registro é estruturado, contém os campos
obrigatórios e que todos os registros daquela operação têm o mesmo identificador devolvido
na resposta.

**Cenários de Aceitação**:

1. **Dado** uma requisição de criação de tarefa, **Quando** a aplicação registra os logs dessa
   requisição, **Então** todos os registros contêm o mesmo identificador devolvido ao cliente.
2. **Dado** qualquer registro de log da aplicação, **Quando** ele é inspecionado, **Então** é
   um registro estruturado com, no mínimo, data/hora, nível de severidade, mensagem, origem e,
   quando dentro de uma requisição, o identificador da requisição e os identificadores de
   rastreamento.
3. **Dado** duas requisições processadas ao mesmo tempo, **Quando** os logs são filtrados pelo
   identificador de uma delas, **Então** apenas os registros daquela requisição aparecem.
4. **Dado** um registro gerado fora de uma requisição (por exemplo, na inicialização da
   aplicação), **Quando** ele é inspecionado, **Então** continua estruturado, apenas sem o
   identificador de requisição.

---

### História de Usuário 3 - Erros com Contexto e sem Dados Sensíveis (Prioridade: P2)

Como desenvolvedora diagnosticando um problema, eu quero que cada erro seja registrado com
contexto suficiente para entender o que falhou, sem que dados sensíveis apareçam nos logs,
para resolver incidentes rápido sem criar riscos de privacidade ou segurança.

**Por que esta prioridade**: Depende dos logs estruturados e correlacionados (P1). Agrega o
valor de diagnóstico, mas o MVP de suporte já funciona sem ele.

**Teste Independente**: Pode ser testado isoladamente provocando erros conhecidos (dados
inválidos, recurso inexistente, banco indisponível) e verificando que o registro do erro traz
os campos de contexto exigidos e não contém nenhum dado sensível.

**Cenários de Aceitação**:

1. **Dado** uma falha inesperada durante uma requisição, **Quando** o erro é registrado,
   **Então** o registro tem severidade de erro, o identificador da requisição, a operação
   (método e rota), o tipo do erro, a mensagem técnica e a pilha de execução.
2. **Dado** um erro esperado de negócio (por exemplo, tarefa não encontrada ou validação
   rejeitada), **Quando** ele é registrado, **Então** o registro tem severidade de aviso (não de
   erro), o identificador da requisição e o motivo, sem pilha de execução.
3. **Dado** que o banco de dados está indisponível, **Quando** uma requisição falha por isso,
   **Então** o erro é registrado com o identificador da requisição e a indicação de qual
   dependência falhou, sem expor credenciais ou o endereço completo de conexão.
4. **Dado** uma requisição que contém credenciais, tokens ou cabeçalhos de autorização,
   **Quando** qualquer log relacionado é gerado, **Então** esses valores não aparecem (são
   omitidos ou mascarados).
5. **Dado** um erro inesperado, **Quando** a resposta é devolvida ao cliente, **Então** ela não
   contém pilha de execução nem detalhes internos, apenas uma mensagem genérica e o
   identificador da requisição.

---

### História de Usuário 4 - Pesquisa de Logs no Ambiente Local (Prioridade: P3)

Como desenvolvedora, eu quero subir, com um único comando, o banco de dados junto com uma
interface onde eu possa pesquisar os logs da aplicação, para investigar o comportamento
localmente da mesma forma que o suporte faria.

**Por que esta prioridade**: Melhora a experiência de desenvolvimento e valida o fluxo de
pesquisa, mas os logs estruturados e correlacionados já entregam valor sem a interface.

**Teste Independente**: Pode ser testado isoladamente executando o comando único de subida do
ambiente, iniciando a aplicação, fazendo algumas chamadas e localizando na interface todos os
logs de uma requisição a partir do identificador devolvido na resposta.

**Cenários de Aceitação**:

1. **Dado** um ambiente local limpo, **Quando** a desenvolvedora executa o comando único de
   subida, **Então** o banco de dados e a interface de pesquisa de logs ficam disponíveis.
2. **Dado** o ambiente local em execução e a aplicação rodando, **Quando** a desenvolvedora faz
   uma chamada e pesquisa pelo identificador devolvido, **Então** a interface mostra todos os
   registros daquela requisição.
3. **Dado** a interface de pesquisa, **Quando** a desenvolvedora filtra por nível de severidade
   ou por intervalo de tempo, **Então** apenas os registros correspondentes são exibidos.
4. **Dado** que a interface de pesquisa de logs está fora do ar, **Quando** a aplicação recebe
   requisições, **Então** elas são atendidas normalmente; a indisponibilidade do destino dos
   logs não afeta o funcionamento da API.

---

### Casos de Borda

- Cliente envia um identificador de rastreamento muito longo ou com caracteres inválidos: o
  valor é descartado, um novo identificador é gerado e a requisição segue normalmente.
- Requisição para uma rota inexistente: a resposta de "não encontrado" também traz o
  identificador e o registro correspondente é gerado.
- Mensagem de erro que inclui, por acidente, um valor sensível (por exemplo, texto de uma
  exceção do driver com a senha de conexão): o valor é mascarado antes de ser registrado.
- Corpo da requisição com conteúdo informado pelo usuário (títulos e descrições de tarefas):
  o corpo completo não é registrado; apenas metadados (tamanho, campos rejeitados na validação).
- Destino central de logs indisponível ou lento: a aplicação continua respondendo dentro do
  tempo normal; os registros continuam saindo na saída padrão da aplicação.
- Volume alto de requisições simultâneas: os registros de requisições diferentes nunca se
  misturam (nenhum registro recebe o identificador de outra requisição).

## Requisitos *(obrigatório)*

### Requisitos Funcionais

- **FR-001**: O sistema MUST emitir todos os logs em formato estruturado (campos nomeados), e
  nunca como texto livre concatenado.
- **FR-002**: Cada registro de log MUST conter, no mínimo: data/hora com fuso, nível de
  severidade, mensagem, nome da aplicação e origem (componente que gerou o registro).
- **FR-003**: O sistema MUST atribuir a cada requisição recebida um identificador único,
  reaproveitando o identificador de requisição enviado pelo cliente quando ele for válido, e
  gerando um novo quando estiver ausente ou inválido. O contexto de rastreamento enviado no
  padrão W3C Trace Context MUST ser continuado de forma independente.
- **FR-004**: Todo registro de log gerado durante o processamento de uma requisição MUST conter
  o identificador da requisição e os identificadores de rastreamento ativos.
- **FR-005**: Toda resposta da API, de sucesso ou de erro, MUST devolver o identificador da
  requisição ao cliente num cabeçalho de resposta.
- **FR-006**: As respostas de erro MUST incluir também o identificador da requisição no corpo,
  mantendo o formato de corpo de erro já usado pela API.
- **FR-007**: O sistema MUST registrar um log por requisição concluída contendo método, rota,
  código de status da resposta e duração.
- **FR-008**: Erros inesperados MUST ser registrados com severidade de erro, contendo o
  identificador da requisição, método e rota, tipo do erro, mensagem técnica e pilha de execução.
- **FR-009**: Erros esperados de negócio (validação rejeitada, recurso não encontrado) MUST ser
  registrados com severidade de aviso, contendo o identificador da requisição e o motivo, sem
  pilha de execução.
- **FR-010**: Falhas de dependências externas (por exemplo, banco de dados) MUST ser registradas
  identificando qual dependência falhou e o tipo de falha (indisponível, tempo esgotado).
- **FR-011**: O sistema MUST NOT registrar dados sensíveis em logs: credenciais, senhas, tokens,
  cabeçalhos de autorização, cookies e strings de conexão completas MUST ser omitidos ou
  mascarados.
- **FR-012**: O sistema MUST NOT registrar o corpo completo das requisições e respostas; apenas
  metadados necessários ao diagnóstico.
- **FR-013**: Respostas de erro devolvidas ao cliente MUST NOT expor pilha de execução, nomes
  de classes internas ou detalhes de infraestrutura.
- **FR-014**: Os logs MUST ser enviados para um destino central onde possam ser pesquisados
  por qualquer campo, incluindo identificador da requisição, severidade e intervalo de tempo.
- **FR-015**: A indisponibilidade ou lentidão do destino central de logs MUST NOT impedir nem
  atrasar de forma perceptível o atendimento das requisições; os logs MUST continuar disponíveis
  na saída padrão da aplicação.
- **FR-016**: O ambiente de desenvolvimento local MUST subir, com um único comando, o banco de
  dados e a interface de pesquisa de logs, usando configuração versionada no repositório.
- **FR-017**: A aplicação rodando localmente MUST enviar seus logs para a interface de pesquisa
  local sem configuração manual adicional por parte da desenvolvedora.
- **FR-018**: O nível mínimo de severidade dos logs MUST ser configurável por ambiente, sem
  alteração de código.
- **FR-019**: O comportamento funcional das operações existentes de tarefas (criar, listar,
  concluir, excluir) MUST permanecer inalterado, exceto pela adição do identificador nas
  respostas.

### Entidades-Chave

- **Requisição**: uma chamada recebida pela API. Atributos relevantes: identificador único,
  método, rota, código de status, duração, contexto de rastreamento associado.
- **Registro de Log**: um evento estruturado emitido pela aplicação. Atributos: data/hora,
  severidade, mensagem, origem, nome da aplicação, identificador da requisição (quando houver),
  identificadores de rastreamento (quando houver) e, para erros, tipo, mensagem técnica e pilha
  de execução.
- **Contexto de Rastreamento**: informação que liga a requisição a uma cadeia maior de
  chamadas, podendo vir do cliente ou ser criada pelo sistema; dele deriva o identificador
  devolvido ao cliente.

## Critérios de Sucesso *(obrigatório)*

### Resultados Mensuráveis

- **SC-001**: 100% das respostas da API (sucesso e erro) trazem o identificador da requisição.
- **SC-002**: 100% dos registros de log gerados durante uma requisição contêm o identificador
  daquela requisição, verificado sob pelo menos 50 requisições simultâneas sem nenhum registro
  atribuído à requisição errada.
- **SC-003**: A partir do identificador informado por um cliente, uma pessoa de suporte localiza
  todos os registros daquela requisição na interface de pesquisa em menos de 1 minuto.
- **SC-004**: Zero ocorrências de dados sensíveis (senhas, tokens, cabeçalhos de autorização,
  strings de conexão) nos logs, verificado por testes automatizados que enviam esses valores e
  inspecionam a saída.
- **SC-005**: Para 100% dos erros inesperados, o registro de erro sozinho permite identificar a
  operação afetada, o tipo de falha e o ponto de origem, sem precisar reproduzir o problema.
- **SC-006**: Uma desenvolvedora com o ambiente limpo consegue, com um único comando, ter banco
  de dados e interface de pesquisa de logs disponíveis em menos de 3 minutos.
- **SC-007**: Com o destino central de logs fora do ar, a API continua atendendo 100% das
  requisições válidas, sem aumento perceptível no tempo de resposta.

## Premissas

- O identificador de requisição é um valor próprio, lido de um cabeçalho dedicado enviado
  pelo cliente ou gerado pelo sistema, e devolvido no mesmo cabeçalho. Ele é separado do
  identificador de rastreamento (W3C Trace Context), que também aparece em todos os logs da
  requisição. O nome do cabeçalho é definido no plano.
- A API não tem autenticação hoje; a regra de mascaramento de dados sensíveis vale de forma
  preventiva para cabeçalhos e valores que venham a existir.
- Títulos e descrições de tarefas são tratados como conteúdo do usuário e não são registrados
  integralmente nos logs.
- O escopo desta funcionalidade é logs e correlação; métricas (RED) e spans exigidos pela
  constituição para cada funcionalidade serão detalhados no plano, aproveitando a mesma
  infraestrutura local de observabilidade.
- A interface de pesquisa de logs é exigida apenas para o ambiente local; a escolha do destino
  em ambientes implantados está fora do escopo desta funcionalidade.
- Retenção de logs no ambiente local segue o padrão da ferramenta escolhida; não há requisito de
  retenção mínima localmente.
- O comando único de subida do ambiente local estende o que já existe para o banco de dados
  (funcionalidade 002), sem exigir passos extras.
