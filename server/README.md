# PsiUnisantos - API Backend (clinica-api)

Este documento explica, passo a passo, como este projeto Spring Boot foi criado e como
rodá-lo localmente. Ele foi escrito pensando em quem nunca desenvolveu uma API em Java
com Spring Boot antes.

## 1. Como o projeto foi criado

O projeto **não foi escrito do zero**. Ele foi gerado usando o **Spring Initializr**
(https://start.spring.io), uma ferramenta oficial do Spring que já monta toda a estrutura
inicial de pastas, arquivos de configuração de build e as dependências escolhidas,
evitando que o time precise configurar tudo manualmente.

### 1.1. Configurações selecionadas no Spring Initializr

| Campo | Valor escolhido |
|---|---|
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 4.1.1 |
| Group | com.unisantos |
| Artifact | clinica-api |
| Package name | com.unisantos.clinica-api |
| Packaging | Jar |
| Configuration | Properties |
| Java | 25 |

### 1.2. Dependências selecionadas e o que cada uma faz

| Dependência | Para que serve |
|---|---|
| **Spring Web** | Permite criar endpoints REST (controllers) e usa o Tomcat embutido como servidor HTTP. É a base de qualquer API. |
| **Spring Data JPA** | Facilita salvar e consultar dados no banco relacional usando Java, sem escrever SQL manualmente na maior parte dos casos. Usa o Hibernate por baixo dos panos. |
| **MySQL Driver** | O "conector" que permite ao Java conversar com um banco de dados MySQL especificamente. |
| **Spring Security** | Framework de autenticação e controle de acesso. Protege os endpoints da API por padrão — é por causa dele que a API pede usuário e senha (mais detalhes na seção 4). |
| **Validation** | Permite validar campos de entrada automaticamente (ex: "este campo é obrigatório", "este e-mail precisa ser válido") usando anotações simples no código. |
| **Flyway Migration** | Controla e versiona as alterações no schema do banco de dados (criação de tabelas, colunas, etc.), como um "Git para o banco de dados". |
| **SpringDoc OpenAPI** | Gera automaticamente uma documentação interativa da API (Swagger UI), listando todos os endpoints disponíveis sem precisarmos escrever a documentação manualmente. |
| **Lombok** | Reduz código repetitivo em Java (getters, setters, construtores) usando anotações, deixando as classes mais enxutas. |
| **Spring Boot Actuator** | Expõe endpoints prontos para monitorar a saúde e as métricas da aplicação (ex: se ela está no ar, uso de memória, etc.). |
| **Prometheus** | Formata as métricas do Actuator no padrão que a ferramenta Prometheus entende, para monitoramento futuro. |
| **Testcontainers** | Permite rodar testes de integração usando um banco de dados real dentro de um container Docker temporário, em vez de simular o banco. |
| **Spring Boot DevTools** | Ferramenta de produtividade: reinicia a aplicação automaticamente sempre que um arquivo do código é alterado e salvo, sem precisar parar e rodar tudo de novo manualmente. |


### 1.3. Download e envio para o repositório

1. No site do Spring Initializr, com as configurações da seção 1.1 e as dependências da
   seção 1.2 selecionadas, clicou-se em **GENERATE**, o que baixou um arquivo `.zip`.
2. O conteúdo do `.zip` foi extraído dentro da pasta `server/` deste repositório
   (não em uma subpasta nova — os arquivos como `build.gradle`, `src/`, `gradlew` ficam
   direto dentro de `server/`).

## 2. Pré-requisitos para rodar o projeto

- Java 25 instalado (ou usar o ambiente já configurado no Codespaces do repositório)
- Docker instalado e rodando (necessário para o banco de dados MySQL local)

Não é necessário instalar o Gradle manualmente — o projeto usa o **Gradle Wrapper**
(`gradlew`), que baixa e usa a versão correta do Gradle sozinho.

## 3. Como rodar a aplicação localmente

1. Entre na pasta do backend:
   ```bash
   cd server
   ```
2. Dê permissão de execução ao wrapper do Gradle (só precisa fazer isso uma vez):
   ```bash
   chmod +x gradlew
   ```
3. Rode a aplicação:
   ```bash
   ./gradlew bootRun
   ```

Ao rodar esse comando, o Spring Boot detecta automaticamente o arquivo `compose.yaml`
presente na pasta `server/` (graças à dependência de suporte a Docker Compose incluída
no projeto) e sobe um container de banco de dados MySQL sozinho, sem você precisar fazer
nada manualmente. Aguarde até aparecer no terminal uma mensagem parecida com:

```
Started ClinicaApiApplication in X seconds
```

Isso confirma que a aplicação subiu com sucesso e está rodando na porta **8080**.

Para parar a aplicação (e o container do MySQL, que é derrubado junto), use `Ctrl+C`
no terminal onde o comando está rodando.

## 4. Autenticação: por que a API pede usuário e senha

Como o **Spring Security** está entre as dependências do projeto, por padrão ele exige
autenticação para acessar qualquer endpoint da aplicação — inclusive para páginas de
diagnóstico, como o Swagger e o Actuator. Isso é intencional e esperado nesta fase do
projeto: nenhuma configuração de login customizada foi feita ainda.

Sempre que a aplicação é iniciada, o Spring gera uma senha aleatória e a exibe no
terminal, em uma linha parecida com esta:

```
Using generated security password: 7x82a4d6-6a14-4635
```

**Essa senha muda a cada vez que a aplicação é reiniciada.** Para acessar qualquer
endpoint protegido, use:

- **Usuário:** `user`
- **Senha:** o UUID exibido no terminal naquele momento

Exemplo de acesso via terminal:

```bash
curl -u user:7x82a4d6-6a14-4635 http://localhost:8080/actuator/health
```

Ao acessar pelo navegador, um popup de login vai aparecer pedindo esses mesmos dados.

## 5. URLs disponíveis para teste

Com a aplicação rodando, os seguintes endereços ficam disponíveis em
`http://localhost:8080`:

| URL | O que mostra |
|---|---|
| `/actuator/health` | Indica se a aplicação está saudável (`{"status":"UP"}`) |
| `/actuator/metrics` | Lista métricas internas da aplicação (memória, threads, requisições, etc.) |
| `/actuator/prometheus` | As mesmas métricas, no formato que o Prometheus entende |
| `/swagger-ui.html` | Interface visual interativa listando todos os endpoints da API |
| `/v3/api-docs` | A especificação OpenAPI da API em formato JSON (usada internamente pelo Swagger UI) |

> Se estiver rodando dentro do GitHub Codespaces, substitua `http://localhost:8080`
> pela URL pública gerada automaticamente na aba **PORTS** do VS Code para a porta 8080.

## 6. Próximos passos

Este README cobre apenas a criação do projeto base e como executá-lo. À medida que os
módulos de Agenda e Sala Virtual forem implementados, novos endpoints e regras de
autenticação (RBAC) serão adicionados, substituindo esse usuário/senha temporário por
um mecanismo de login real.
