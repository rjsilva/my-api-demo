
# Especificação da Funcionalidade: Gerenciamento de Tarefas

**Branch da Funcionalidade**: `001-task-management`

**Criado em**: 2026-11-13

**Status**: Draft

**Entrada**: Descrição do usuário: "Uma API de gerenciamento de tarefas. O usuário pode criar uma tarefa com título e descrição opcional, listar todas as tarefas, marcar uma tarefa como concluída e excluir uma tarefa. Tarefas concluídas aparecem no fim da lista. O título é obrigatório e tem no máximo 100 caracteres."

## Clarifications

### Session 2026-11-13

- Q: O sistema precisa identificar/autenticar usuários diferentes, ou cada instância da API serve um único usuário (sem login)? → A: Mono-usuário, sem login; todas as tarefas pertencem à mesma lista global.

## Cenários de Usuário e Testes *(obrigatório)*

### História de Usuário 1 - Criar Tarefa (Prioridade: P1)

Como usuário, eu quero criar uma nova tarefa informando um título e, opcionalmente, uma
descrição, para registrar algo que preciso fazer.

**Por que esta prioridade**: Sem a capacidade de criar tarefas não há nenhum valor entregue —
é a capacidade fundamental sobre a qual todas as outras dependem.

**Teste Independente**: Pode ser testado isoladamente enviando uma requisição de criação com
título válido (com e sem descrição) e verificando que a tarefa é retornada com status pendente.

**Cenários de Aceitação**:

1. **Dado** que não existem tarefas, **Quando** o usuário cria uma tarefa com título "Comprar
   leite" e sem descrição, **Então** a tarefa é criada com status pendente e sem descrição.
2. **Dado** que não existem tarefas, **Quando** o usuário cria uma tarefa com título "Estudar
   Spring Boot" e descrição "Capítulos 1 a 3", **Então** a tarefa é criada com status pendente,
   título e descrição informados.
3. **Dado** qualquer estado do sistema, **Quando** o usuário tenta criar uma tarefa sem título
   (vazio ou apenas espaços), **Então** o sistema rejeita a criação e informa o motivo.
4. **Dado** qualquer estado do sistema, **Quando** o usuário tenta criar uma tarefa com título
   de mais de 100 caracteres, **Então** o sistema rejeita a criação e informa o motivo.

---

### História de Usuário 2 - Listar Tarefas (Prioridade: P1)

Como usuário, eu quero listar todas as minhas tarefas, com as concluídas aparecendo no fim da
lista, para visualizar rapidamente o que ainda preciso fazer.

**Por que esta prioridade**: Visualizar as tarefas criadas é essencial para que a funcionalidade
tenha utilidade prática; sem listagem, o usuário não consegue acompanhar o que cadastrou.

**Teste Independente**: Pode ser testado isoladamente criando algumas tarefas, marcando parte
delas como concluídas, e verificando que a listagem retorna as pendentes antes das concluídas.

**Cenários de Aceitação**:

1. **Dado** que existem tarefas pendentes e concluídas, **Quando** o usuário lista as tarefas,
   **Então** todas as tarefas pendentes aparecem antes de todas as tarefas concluídas.
2. **Dado** que não existem tarefas cadastradas, **Quando** o usuário lista as tarefas,
   **Então** a lista retornada é vazia.
3. **Dado** que existem múltiplas tarefas pendentes, **Quando** o usuário lista as tarefas,
   **Então** elas aparecem na ordem em que foram criadas (da mais antiga para a mais recente).

---

### História de Usuário 3 - Marcar Tarefa como Concluída (Prioridade: P2)

Como usuário, eu quero marcar uma tarefa como concluída, para indicar que ela já foi realizada.

**Por que esta prioridade**: Depende da criação e listagem já existirem, mas é o que dá sentido
ao ciclo de vida da tarefa (pendente → concluída).

**Teste Independente**: Pode ser testado isoladamente criando uma tarefa, marcando-a como
concluída e verificando que seu status muda e que ela passa a aparecer no fim da listagem.

**Cenários de Aceitação**:

1. **Dado** uma tarefa pendente existente, **Quando** o usuário a marca como concluída,
   **Então** seu status passa a concluída e ela passa a aparecer no fim da lista.
2. **Dado** uma tarefa que não existe, **Quando** o usuário tenta marcar essa tarefa como
   concluída, **Então** o sistema informa que a tarefa não foi encontrada.

---

### História de Usuário 4 - Excluir Tarefa (Prioridade: P3)

Como usuário, eu quero excluir uma tarefa, para remover algo que não é mais relevante.

**Por que esta prioridade**: É uma operação de manutenção da lista; tem valor, mas o sistema já
é funcional sem ela (o usuário pode simplesmente ignorar tarefas concluídas).

**Teste Independente**: Pode ser testado isoladamente criando uma tarefa, excluindo-a e
verificando que ela não aparece mais na listagem.

**Cenários de Aceitação**:

1. **Dado** uma tarefa existente (pendente ou concluída), **Quando** o usuário a exclui,
   **Então** ela deixa de aparecer em qualquer listagem futura.
2. **Dado** uma tarefa que não existe, **Quando** o usuário tenta excluí-la, **Então** o
   sistema informa que a tarefa não foi encontrada.

---

### Casos de Borda

- O que acontece quando o título contém apenas espaços em branco? Deve ser tratado como título
  ausente e rejeitado.
- O que acontece quando o título tem exatamente 100 caracteres? Deve ser aceito (limite
  inclusive).
- O que acontece quando o título tem 101 caracteres ou mais? Deve ser rejeitado.
- O que acontece ao tentar marcar como concluída uma tarefa que já está concluída? O sistema
  mantém a tarefa como concluída, sem erro (operação idempotente).
- O que acontece ao tentar marcar como concluída ou excluir uma tarefa inexistente? O sistema
  retorna um erro claro indicando que a tarefa não foi encontrada.

## Requisitos *(obrigatório)*

### Requisitos Funcionais

- **FR-001**: O sistema MUST permitir que o usuário crie uma tarefa informando um título
  (obrigatório) e uma descrição (opcional).
- **FR-002**: O sistema MUST rejeitar a criação de uma tarefa cujo título esteja ausente, vazio
  ou contenha apenas espaços em branco.
- **FR-003**: O sistema MUST rejeitar a criação de uma tarefa cujo título exceda 100 caracteres.
- **FR-004**: O sistema MUST permitir listar todas as tarefas cadastradas.
- **FR-005**: O sistema MUST exibir, em qualquer listagem, todas as tarefas pendentes antes de
  todas as tarefas concluídas.
- **FR-006**: Dentro de cada grupo (pendentes e concluídas), o sistema MUST manter a ordem de
  criação das tarefas, da mais antiga para a mais recente.
- **FR-007**: O sistema MUST permitir que o usuário marque uma tarefa existente como concluída.
- **FR-008**: O sistema MUST permitir que o usuário exclua uma tarefa existente,
  independentemente do seu status (pendente ou concluída).
- **FR-009**: O sistema MUST retornar um erro claro e identificável quando o usuário tentar
  marcar como concluída ou excluir uma tarefa que não existe.
- **FR-010**: Marcar uma tarefa já concluída como concluída novamente MUST ser uma operação
  idempotente (não gera erro, mantém o estado concluído).
- **FR-011**: O sistema MUST operar em modo mono-usuário nesta versão: não há autenticação,
  autorização ou login, e todas as tarefas pertencem a uma única lista global.

### Entidades Principais

- **Tarefa**: representa um item de trabalho a ser realizado. Atributos: identificador único,
  título (obrigatório, até 100 caracteres), descrição (opcional), status (pendente ou
  concluída), momento de criação (usado para ordenação dentro de cada grupo de status).

## Critérios de Sucesso *(obrigatório)*

### Resultados Mensuráveis

- **SC-001**: Usuários conseguem criar uma nova tarefa válida em uma única requisição, sem
  necessidade de tentativas repetidas.
- **SC-002**: Em 100% das listagens, nenhuma tarefa concluída aparece antes de uma tarefa
  pendente.
- **SC-003**: 100% das tentativas de criar tarefa com título ausente ou com mais de 100
  caracteres são rejeitadas com uma mensagem de erro compreensível.
- **SC-004**: 100% das tentativas de marcar como concluída ou excluir uma tarefa inexistente
  resultam em uma resposta de erro clara, sem comportamento inesperado do sistema.

## Premissas

- A exclusão de uma tarefa é definitiva (remoção permanente), sem possibilidade de recuperação
  posterior.
- Não é possível reverter uma tarefa concluída para o status pendente nesta versão (fora de
  escopo).
- Não há paginação na listagem nesta versão; todas as tarefas cadastradas são retornadas em uma
  única resposta.
- Não há limite máximo de caracteres definido para a descrição da tarefa.