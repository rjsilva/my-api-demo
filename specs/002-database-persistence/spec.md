# Especificação da Funcionalidade: Persistência Durável de Tarefas

**Branch da Funcionalidade**: `feat/002-database-persistence`

**Criado em**: 2026-11-15

**Status**: Draft

**Entrada**: Descrição do usuário: "As tarefas devem ser persistidas de forma durável, para não serem perdidas quando a aplicação reiniciar. Todas as operações existentes (criar, listar, concluir, excluir) devem continuar funcionando igual, agora com os dados salvos permanentemente. O ambiente de desenvolvimento deve subir a base de dados com um único comando."

## Clarifications

### Session 2026-11-15

- Q: Depois desta funcionalidade, o armazenamento em memória atual deve ser removido ou mantido como opção alternativa? → A: Removido; o armazenamento durável passa a ser o único.
- Q: Os testes automatizados que dependem do armazenamento devem rodar contra o mesmo tipo de base de dados usado de verdade ou contra uma base leve substituta? → A: Mesmo tipo de base, criada automaticamente durante a execução dos testes e descartada no fim.
- Q: Quando a base de dados estiver fora do ar durante uma operação, a API deve responder com 503 ou 500? → A: 503 (serviço indisponível), no mesmo formato de corpo de erro já usado pela API, com mensagem genérica.
- Q: Se a base de dados ainda não estiver pronta quando a aplicação iniciar, a aplicação deve falhar na hora ou esperar um tempo limitado? → A: Tentar novamente por até 30 segundos; se a base não responder, falhar com mensagem clara.
- Q: As mudanças futuras na estrutura de armazenamento devem ser registradas como histórico versionado ou ajustadas automaticamente sem histórico? → A: Histórico versionado de mudanças, guardado no repositório e aplicado em ordem, automaticamente, na inicialização.

## Cenários de Usuário e Testes *(obrigatório)*

### História de Usuário 1 - Tarefas Sobrevivem ao Reinício da Aplicação (Prioridade: P1)

Como usuário, eu quero que as tarefas que criei, concluí ou excluí continuem no mesmo estado
depois que a aplicação for reiniciada, para não perder o meu trabalho.

**Por que esta prioridade**: É o objetivo central da funcionalidade. Hoje todas as tarefas se
perdem a cada reinício, o que torna a aplicação inviável para uso real.

**Teste Independente**: Pode ser testado isoladamente criando algumas tarefas, concluindo e
excluindo parte delas, reiniciando a aplicação e verificando que a listagem retorna exatamente
o mesmo conjunto de tarefas, com os mesmos dados, status e ordem de antes do reinício.

**Cenários de Aceitação**:

1. **Dado** que o usuário criou as tarefas "A" e "B", **Quando** a aplicação é reiniciada e o
   usuário lista as tarefas, **Então** "A" e "B" aparecem com o mesmo identificador, título,
   descrição, status e ordem de antes do reinício.
2. **Dado** que o usuário marcou a tarefa "A" como concluída, **Quando** a aplicação é
   reiniciada, **Então** "A" continua concluída e continua aparecendo no fim da lista.
3. **Dado** que o usuário excluiu a tarefa "B", **Quando** a aplicação é reiniciada, **Então**
   "B" não aparece na listagem e tentar concluí-la ou excluí-la retorna "não encontrada".
4. **Dado** que existem tarefas salvas de execuções anteriores, **Quando** o usuário cria uma
   nova tarefa após o reinício, **Então** ela recebe um identificador diferente de todas as
   tarefas existentes ou já excluídas e aparece depois das pendentes mais antigas.

---

### História de Usuário 2 - Operações Existentes Continuam Iguais (Prioridade: P1)

Como usuário (ou cliente da API), eu quero que criar, listar, concluir e excluir tarefas
funcionem exatamente como antes, para não precisar mudar nada na forma como uso o sistema.

**Por que esta prioridade**: A troca do armazenamento não pode quebrar quem já usa a API.
Qualquer mudança de comportamento seria uma regressão.

**Teste Independente**: Pode ser testado isoladamente executando todos os cenários de aceitação
da funcionalidade 001 (Gerenciamento de Tarefas) contra a aplicação com armazenamento durável e
verificando que todos passam sem alteração nas requisições ou nas respostas esperadas.

**Cenários de Aceitação**:

1. **Dado** o armazenamento durável ativo, **Quando** o usuário executa qualquer operação
   (criar, listar, concluir, excluir), **Então** o formato da requisição, o formato da
   resposta, as mensagens de erro e as regras de validação são idênticos aos da funcionalidade
   001.
2. **Dado** o armazenamento durável ativo, **Quando** o usuário tenta criar uma tarefa com
   título vazio ou com mais de 100 caracteres, **Então** a criação é rejeitada e nada é salvo.
3. **Dado** o armazenamento durável ativo, **Quando** o usuário lista as tarefas, **Então** as
   pendentes aparecem antes das concluídas e, em cada grupo, na ordem de criação.

---

### História de Usuário 3 - Subir a Base de Dados de Desenvolvimento com Um Comando (Prioridade: P2)

Como desenvolvedor, eu quero subir a base de dados local com um único comando, para começar a
desenvolver e testar a aplicação sem passos manuais de instalação ou configuração.

**Por que esta prioridade**: Não muda o comportamento para o usuário final, mas é necessário
para que qualquer pessoa da equipe consiga rodar a aplicação com persistência localmente.

**Teste Independente**: Pode ser testado isoladamente em uma máquina com os pré-requisitos
documentados: executar o comando documentado, iniciar a aplicação e criar/listar uma tarefa
com sucesso, sem nenhuma outra configuração manual.

**Cenários de Aceitação**:

1. **Dado** uma máquina de desenvolvimento com os pré-requisitos documentados e um clone limpo
   do repositório, **Quando** o desenvolvedor executa o comando documentado, **Então** a base
   de dados fica disponível e a aplicação consegue se conectar a ela sem configuração adicional.
2. **Dado** a base de dados de desenvolvimento já em execução com tarefas salvas, **Quando** o
   desenvolvedor a para e a sobe novamente com o mesmo comando, **Então** as tarefas salvas
   continuam disponíveis.
3. **Dado** um desenvolvedor novo no projeto, **Quando** ele consulta a documentação do
   projeto, **Então** encontra o comando para subir a base de dados, o comando para pará-la e
   como apagar os dados locais para recomeçar do zero.

---

### Casos de Borda

- O que acontece se a base de dados estiver indisponível quando a aplicação inicia? A aplicação
  tenta novamente por até 30 segundos sem atender requisições; se a base aparecer nesse
  intervalo, inicia normalmente; caso contrário, encerra com uma mensagem clara informando que
  não conseguiu acessar o armazenamento.
- O que acontece se a base de dados ficar indisponível durante uma operação? A operação deve
  retornar 503 (serviço indisponível) com mensagem genérica (sem expor detalhes internos), e
  nenhum dado parcial ou inconsistente deve ser salvo.
- O que acontece se a aplicação for interrompida no meio de uma operação? Após o reinício, a
  operação deve ter sido aplicada por completo ou não ter sido aplicada; nunca pela metade.
- O que acontece com identificadores de tarefas excluídas? Eles não são reutilizados por novas
  tarefas, nem depois de reinícios.
- O que acontece com tarefas criadas na mesma fração de segundo? A ordem de criação entre elas
  continua determinística e é preservada após o reinício.
- O que acontece com a descrição longa ou com caracteres especiais/acentuados? É salva e
  devolvida exatamente como foi enviada.

## Requisitos *(obrigatório)*

### Requisitos Funcionais

- **FR-001**: O sistema MUST salvar de forma durável toda tarefa criada, de modo que ela
  continue disponível após qualquer reinício da aplicação.
- **FR-002**: O sistema MUST salvar de forma durável a mudança de status de uma tarefa para
  concluída.
- **FR-003**: O sistema MUST remover de forma permanente uma tarefa excluída, de modo que ela
  não reapareça após reinícios.
- **FR-004**: Após um reinício, o sistema MUST devolver cada tarefa com o mesmo identificador,
  título, descrição, status e momento de criação que tinha antes.
- **FR-005**: O sistema MUST manter a regra de ordenação da funcionalidade 001 (pendentes antes
  das concluídas; dentro de cada grupo, da mais antiga para a mais recente) considerando todas
  as tarefas salvas, inclusive as de execuções anteriores.
- **FR-006**: O sistema MUST atribuir a cada nova tarefa um identificador único que nunca
  coincida com o de uma tarefa existente ou já excluída, inclusive após reinícios.
- **FR-007**: O sistema MUST manter inalterados o formato das requisições e respostas, as regras
  de validação, as mensagens de erro e os resultados de todas as operações definidas na
  funcionalidade 001 (criar, listar, concluir, excluir).
- **FR-008**: Cada operação de escrita (criar, concluir, excluir) MUST ser atômica: ou é salva
  por completo, ou não tem efeito nenhum.
- **FR-009**: Quando o armazenamento estiver indisponível durante uma operação, o sistema MUST
  responder com o status 503 (serviço indisponível), no mesmo formato de corpo de erro já usado
  pela API, com uma mensagem genérica que não expõe detalhes internos, e sem salvar dados
  parciais.
- **FR-010**: Quando o armazenamento estiver indisponível na inicialização, o sistema MUST
  tentar se conectar novamente por até 30 segundos; se o armazenamento continuar indisponível,
  MUST encerrar a inicialização com uma mensagem clara informando que não conseguiu acessá-lo,
  sem aceitar requisições em nenhum momento desse intervalo.
- **FR-011**: O projeto MUST oferecer um único comando, documentado, que sobe a base de dados de
  desenvolvimento pronta para uso pela aplicação, sem passos manuais de instalação ou
  configuração além dos pré-requisitos documentados.
- **FR-012**: Os dados salvos na base de dados de desenvolvimento MUST continuar disponíveis
  quando ela for parada e iniciada novamente, até que o desenvolvedor decida apagá-los
  explicitamente.
- **FR-013**: A estrutura de armazenamento das tarefas MUST ser definida por um histórico
  versionado de mudanças, guardado no repositório e aplicado em ordem, automaticamente, na
  inicialização da aplicação; cada mudança é aplicada uma única vez, preserva os dados
  existentes e não exige passos manuais de preparação.
- **FR-014**: A documentação do projeto MUST explicar como subir, parar e apagar os dados da base
  de dados de desenvolvimento.
- **FR-015**: O armazenamento durável MUST ser o único armazenamento de tarefas do sistema; o
  armazenamento em memória atual MUST ser removido e não pode ser ativado por configuração.
- **FR-016**: Os testes automatizados que dependem do armazenamento MUST rodar contra o mesmo
  tipo de base de dados usado pela aplicação, criada automaticamente durante a execução dos
  testes e descartada ao final, sem exigir que a base de desenvolvimento esteja rodando nem
  qualquer passo manual.

### Entidades Principais

- **Tarefa**: a mesma entidade da funcionalidade 001, agora armazenada de forma durável.
  Atributos: identificador único (nunca reutilizado), título (obrigatório, até 100 caracteres),
  descrição (opcional), status (pendente ou concluída) e momento de criação (usado para
  ordenação). Nenhum atributo novo é visível ao usuário.

## Critérios de Sucesso *(obrigatório)*

### Resultados Mensuráveis

- **SC-001**: Após reiniciar a aplicação, 100% das tarefas existentes antes do reinício
  aparecem na listagem com os mesmos dados, status e ordem; e 0 tarefas excluídas reaparecem.
- **SC-002**: 100% dos cenários de aceitação da funcionalidade 001 continuam passando sem
  qualquer alteração nas requisições ou nas respostas esperadas.
- **SC-003**: Um desenvolvedor com os pré-requisitos instalados consegue, a partir de um clone
  limpo, deixar a base de dados pronta executando 1 único comando, em menos de 2 minutos
  (desconsiderando o primeiro download de dependências).
- **SC-004**: Em 100% dos testes de interrupção (aplicação parada logo após uma operação
  confirmada ao usuário), a operação confirmada está refletida após o reinício.
- **SC-005**: Os usuários não percebem diferença de tempo de resposta: criar, concluir, excluir
  e listar uma lista de até 1.000 tarefas continuam respondendo em menos de 1 segundo.

## Premissas

- O escopo continua mono-usuário, sem autenticação, conforme a funcionalidade 001.
- Não há migração de dados do armazenamento atual em memória: como ele é perdido a cada
  reinício, não existem dados a preservar no momento da troca.
- O comando único é voltado apenas ao ambiente de desenvolvimento local; provisionamento da
  base de dados em produção, backup e recuperação de desastres estão fora de escopo.
- Os pré-requisitos da máquina de desenvolvimento (por exemplo, a ferramenta usada para rodar
  a base de dados localmente) serão definidos no plano e documentados no projeto.
- Os testes automatizados devem continuar podendo ser executados de forma isolada e
  reproduzível, sem depender de dados deixados por execuções anteriores (ver FR-016).
- Não há paginação, busca ou novos campos nesta funcionalidade; o comportamento funcional é o
  mesmo da funcionalidade 001.
