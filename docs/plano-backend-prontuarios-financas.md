# Plano de backend — Cadastro, Prontuários e Finanças

Squad Cadastro · issues #6, #8 e #11

Escrevi este documento como resposta ao pedido de enviar o plano em markdown antes
de começar a implementar. A ideia é justamente essa: alinhar agora, ajustar o que
precisar, e só depois abrir código. Se algum ponto conflitar com o que já está
definido na agenda ou no diagrama do banco, é melhor resolver aqui do que em merge.

## Como pretendo seguir

Nada de mudança de stack. Fico com o que já está no `build.gradle`: Java 25,
Spring Boot 4.1.1, Gradle, MySQL 8.4 e Flyway para as migrations, mais JPA,
Spring Security, Bean Validation e springdoc para a documentação da API. Testes
com JUnit e Testcontainers, que já estão declarados.

Nesta primeira etapa vai tudo em um perfil administrativo único. Sei que a
matriz de permissões do nosso documento prevê secretaria, coordenação e um perfil
financeiro separado, mas separar autorização agora só atrasaria a entrega das
tabelas, que é o que está travando o front. Como a separação de perfis mexe
apenas na camada de autorização e não nas entidades, dá para fazer depois sem
retrabalho de modelagem.

## O que não vou recriar

Esse é o ponto que mais me preocupava. No primeiro semestre cada grupo modelou
sozinho, e se repetirmos isso agora vamos acabar com duas tabelas de paciente.
Então, tudo que já está no diagrama do banco eu trato como existente e apenas
referencio:

`Tipo_Usuario`, `Tipo_Modalidade`, `Especialidade`, `Profissional`, `Paciente`,
`Sessao`, `Sala`, `Configuracao_Disponibilidade`, `Anamnese`, `Anamnese_Queixa`,
`Queixas_Anamnese`, `Anamnese_Emocao` e `Emocoes_Anamnese`.

Duas coisas aí dependem de confirmação de vocês.

A primeira é a `Anamnese`. Ela já está modelada no diagrama, com os N:M de
queixas e emoções, mas anamnese também aparecia no escopo de prontuários no
documento do nosso grupo. Estou assumindo que ela continua pertencendo à agenda e
que o módulo de prontuários só a vincula ao prontuário, sem mexer na estrutura.
Se for assim, fechado; se não, me avisem antes de eu escrever a migration.

A segunda é `Usuario` e `Paciente`. Se já estiverem completas no diagrama, a
issue #6 vira complementar o que faltar (credenciais, status, vínculo com
`Tipo_Usuario`) em vez de criar a tabela do zero.

## Tabelas novas

Segui a convenção que já está no diagrama: `snake_case`, chave primária
`id_<entidade>`, `int [pk, increment]`. Dá para colar direto no dbdiagram e ver
se encaixa.

```dbml
Table Prontuario {
  id_prontuario        int [pk, increment]
  id_paciente          int [not null]
  data_abertura        datetime [not null]
  data_arquivamento    datetime [null]
  status_prontuario    varchar(20) [not null]  // ATIVO, ARQUIVADO
  id_usuario_abertura  int [not null]
}

Table Vinculo_Profissional {
  id_vinculo           int [pk, increment]
  id_prontuario        int [not null]
  id_profissional      int [not null]          // responsável pelo atendimento
  id_supervisor        int [null]
  data_inicio          date [not null]
  data_fim             date [null]             // null = vínculo vigente
  motivo_encerramento  varchar(255) [null]
}

Table Evolucao {
  id_evolucao          int [pk, increment]
  id_prontuario        int [not null]
  id_sessao            int [null]              // referencia Sessao quando houver
  id_profissional      int [not null]
  data_registro        datetime [not null]
  data_finalizacao     datetime [null]
  conteudo             text [not null]
  status_evolucao      varchar(20) [not null]  // RASCUNHO, FINALIZADA
  id_evolucao_origem   int [null]              // complemento aponta para a original
}

Table Anexo_Prontuario {
  id_anexo               int [pk, increment]
  id_prontuario          int [not null]
  nome_arquivo           varchar(255) [not null]
  tipo_mime              varchar(100) [not null]
  tamanho_bytes          bigint [not null]
  caminho_armazenamento  varchar(500) [not null]
  hash_conteudo          varchar(64) [not null]
  id_usuario_upload      int [not null]
  data_upload            datetime [not null]
}

Table Categoria_Financeira {
  id_categoria     int [pk, increment]
  nome_categoria   varchar(100) [not null]
  tipo_categoria   varchar(10) [not null]      // DESPESA, ENTRADA
  ativa            boolean [not null, default: true]
}

Table Registro_Financeiro {
  id_registro              int [pk, increment]
  id_categoria             int [not null]
  tipo_movimentacao        varchar(10) [not null]   // DESPESA, ENTRADA
  valor                    decimal(12,2) [not null]
  data_competencia         date [not null]
  descricao                varchar(255) [null]
  id_usuario_responsavel   int [not null]
  status_registro          varchar(20) [not null]   // ATIVO, CANCELADO
  justificativa_alteracao  varchar(255) [null]
  data_criacao             datetime [not null]
}

Table Comprovante_Financeiro {
  id_comprovante         int [pk, increment]
  id_registro            int [not null]
  nome_arquivo           varchar(255) [not null]
  tipo_mime              varchar(100) [not null]
  caminho_armazenamento  varchar(500) [not null]
  data_upload            datetime [not null]
}

Table Log_Auditoria {
  id_log       bigint [pk, increment]
  id_usuario   int [not null]
  acao         varchar(50) [not null]   // CRIAR, EDITAR, VISUALIZAR, ARQUIVAR, CANCELAR, EXPORTAR
  entidade     varchar(50) [not null]
  id_entidade  int [not null]
  data_hora    datetime [not null]
  ip_origem    varchar(45) [null]
  detalhe      json [null]
}

Ref: Prontuario.id_paciente > Paciente.id_paciente
Ref: Prontuario.id_usuario_abertura > Usuario.id_usuario

Ref: Vinculo_Profissional.id_prontuario > Prontuario.id_prontuario
Ref: Vinculo_Profissional.id_profissional > Profissional.id_profissional
Ref: Vinculo_Profissional.id_supervisor > Profissional.id_profissional

Ref: Evolucao.id_prontuario > Prontuario.id_prontuario
Ref: Evolucao.id_sessao > Sessao.id_sessao
Ref: Evolucao.id_profissional > Profissional.id_profissional
Ref: Evolucao.id_evolucao_origem > Evolucao.id_evolucao

Ref: Anexo_Prontuario.id_prontuario > Prontuario.id_prontuario

Ref: Registro_Financeiro.id_categoria > Categoria_Financeira.id_categoria
Ref: Registro_Financeiro.id_usuario_responsavel > Usuario.id_usuario
Ref: Comprovante_Financeiro.id_registro > Registro_Financeiro.id_registro

Ref: Log_Auditoria.id_usuario > Usuario.id_usuario
```

Sobre índices, os que fazem diferença de verdade são `Prontuario` por
`(id_paciente, status_prontuario)` para a busca e o filtro, `Vinculo_Profissional`
por `(id_prontuario, data_fim)` para achar o vínculo vigente e por
`(id_profissional, data_fim)` para listar os prontuários de um profissional,
`Evolucao` por `(id_prontuario, data_registro)` para o histórico cronológico,
`Registro_Financeiro` por `(data_competencia, id_categoria)` para os relatórios e
`Log_Auditoria` por `(entidade, id_entidade, data_hora)` para a trilha de um
registro específico.

Uma observação técnica: o MySQL não tem índice único parcial, então a regra de um
único vínculo vigente por prontuário (`data_fim IS NULL`) vai ser garantida na
camada de serviço, dentro da transação de transferência, e não por constraint.

## Organização do código

```
com.unisantos.clinica_api
├── common
│   ├── config          // CORS, OpenAPI, Jackson
│   ├── security        // autenticação, filtro, contexto de usuário
│   ├── exception       // handler global e respostas de erro padronizadas
│   └── audit           // interceptador de auditoria
├── cadastro
│   ├── usuario
│   └── paciente
├── prontuario
│   ├── prontuario
│   ├── evolucao
│   ├── anexo
│   └── vinculo
└── financeiro
    ├── categoria
    ├── registro
    └── relatorio
```

Cada subpacote com `entity`, `repository`, `service`, `controller`, `dto` e
`mapper`. Regra de negócio fica no service; controller só valida entrada e
orquestra.

## Migrations

Arquivos em `server/src/main/resources/db/migration`, no padrão
`V<n>__<modulo>_<descricao>.sql`.

Como somos três squads mexendo no mesmo diretório, proponho reservar faixas de
numeração para não dar conflito de versão no Flyway: `V1` a `V19` para a base
compartilhada e a agenda, `V20` a `V39` para cadastro e prontuários, `V40` a
`V59` para o financeiro. Se ninguém se opuser, sigo assim.

Na nossa faixa a sequência fica:

```
V20__cadastro_usuario.sql
V21__prontuario_tabela.sql
V22__prontuario_vinculo_profissional.sql
V23__prontuario_evolucao.sql
V24__prontuario_anexo.sql
V25__auditoria_log.sql
V40__financeiro_categoria.sql
V41__financeiro_registro.sql
V42__financeiro_comprovante.sql
```

## Endpoints

Tudo sob `/api/v1`, com documentação automática pelo springdoc.

No cadastro: `POST /auth/login`, e o CRUD de `/usuarios` e `/pacientes`, com
`PATCH /usuarios/{id}/status` para ativar e inativar em vez de excluir.

Nos prontuários: `GET /prontuarios` com filtro por paciente, profissional,
período e status; `POST /prontuarios` para abertura; `GET /prontuarios/{id}`;
`PATCH /prontuarios/{id}/arquivar` e `/reabrir`. O histórico de responsáveis fica
em `GET /prontuarios/{id}/vinculos` e a transferência em `POST` na mesma rota.
As evoluções ficam em `GET` e `POST /prontuarios/{id}/evolucoes`, com
`PUT /evolucoes/{id}` permitido só enquanto for rascunho,
`PATCH /evolucoes/{id}/finalizar` e `POST /evolucoes/{id}/complementos` para o
caso de precisar corrigir algo depois de finalizada. Anexos em
`GET` e `POST /prontuarios/{id}/anexos`.

No financeiro: `/financeiro/categorias` com criação, listagem e
`PATCH /{id}/desativar`; `/financeiro/registros` com listagem filtrada,
lançamento, alteração e `PATCH /{id}/cancelar`, sendo que alteração e
cancelamento exigem justificativa; comprovantes em
`POST /financeiro/registros/{id}/comprovantes`; e os relatórios em
`GET /financeiro/relatorios` e `/relatorios/exportar`.

Não existe verbo `DELETE` em nenhuma rota, pelo motivo da seção seguinte.

## O que a API precisa garantir

Nada é excluído, em lugar nenhum. A retenção de prontuário é exigida pelo CFP e
pelo CRP, e a LGPD entra junto. Inativação e cancelamento fazem o papel da
exclusão em todos os casos.

Evolução finalizada não pode ser alterada. Depois de finalizada, qualquer
correção ou complemento vira um registro novo apontando para o original pelo
`id_evolucao_origem`. O texto original nunca é sobrescrito.

Toda operação relevante grava em `Log_Auditoria` quem fez, o que fez, em qual
entidade e quando. Isso vai como interceptador, não espalhado por dentro de cada
service.

A transferência de responsabilidade é transacional: encerra o vínculo anterior
preenchendo `data_fim` e cria o novo no mesmo commit, mantendo o histórico
completo de quem atendeu o paciente ao longo do tempo. Esse é o caso de uso que
mais importa na clínica, já que a rotatividade de estagiário é semestral.

Clínico e financeiro ficam separados. Relatório financeiro não expõe anamnese,
evolução nem conteúdo de sessão, e não há chave estrangeira ligando os dois
domínios.

Valor monetário em `DECIMAL(12,2)`, nunca float.

## Ordem de entrega

Começo por usuário, autenticação, paciente, prontuário e vínculo profissional.
Isso é o mínimo para o front do módulo sair do stub e passar a consumir dado
real. Depois entra evolução clínica com o versionamento e o log de auditoria, na
sequência os anexos, então categorias e registros financeiros, e por último
relatórios e exportação em PDF e CSV.

## O que preciso que o grupo defina

1. Se a `Anamnese` continua com a agenda e o nosso módulo só a vincula ao
   prontuário.

2. Criptografia. Isso muda o tipo das colunas: campo cifrado no cliente precisa
   ser `VARBINARY` ou `BLOB`, não `VARCHAR`. Se a decisão vier depois das
   migrations, vira retrabalho de schema. E tem um conflito a resolver antes:
   se o nome do paciente for cifrado no cliente, a busca por nome e os
   relatórios agregados não funcionam do lado do servidor. Nosso grupo pode
   assumir esse módulo, mas a definição precisa sair antes de eu escrever a
   fase 1.

3. As faixas de numeração das migrations, para travar isso antes de alguém criar
   um `V6__` duplicado.

4. Onde guardar anexo. Minha proposta é arquivo no sistema de arquivos da VM com
   só os metadados e o caminho no banco. Guardar o binário no MySQL pesa o banco
   e complica o backup. Isso muda o campo `caminho_armazenamento`.

5. Se `Usuario` e `Paciente` já estão completas no diagrama, para eu só
   complementar em vez de redefinir.

6. Os perfis. Vai tudo em admin nesta fase, mas em algum momento precisamos
   decidir: o `Tipo_Usuario` já prevê `COORDENADOR`, o front hoje trabalha com
   admin, psicólogo e secretária, e não existe perfil administrativo ou
   financeiro em lugar nenhum. O módulo financeiro depende disso para ter
   sentido.
