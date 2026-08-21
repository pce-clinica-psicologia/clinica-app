## Estrutura do Projeto (Monorepo)

Este repositório adota a arquitetura de **Monorepo**, unificando o código do cliente e do servidor no mesmo lugar. O sistema é dividido em domínios de negócio para facilitar o trabalho paralelo das squads multifuncionais.

---

## Ambiente de Desenvolvimento (Docker Compose)

Para garantir que todos os desenvolvedores tenham um ambiente idêntico e livre de conflitos, utilizamos o **Docker Compose**.

Com um único comando na raiz do projeto (`docker-compose up --build`), o Docker se encarrega de:
*   Provisionar o banco de dados MySQL com as credenciais locais.
*   Isolar e compilar o servidor Spring Boot na porta 8080.
*   Isolar, instalar dependências e rodar o cliente React na porta 3000.

---

## Estratégia de Testes

Para mantermos a estabilidade do código com 15 pessoas alterando o repositório simultaneamente, adotamos duas camadas principais de testes:

### Testes Unitários (Rápidos e Isolados)
Garantem que as menores partes do código funcionem conforme o esperado, sem depender de recursos externos.
*   **Server:** Validação das regras de negócio e serviços em Java utilizando JUnit.
*   **Client:** Verificação da renderização de componentes React e lógicas de estado.

### Testes Ponta a Ponta (E2E) com Cypress
path: ./client/cypress/

Validam a integração real entre o client, o server e o banco de dados. O Cypress simula um usuário humano abrindo o navegador, clicando na interface, preenchendo formulários de pacientes e verificando se o resultado final aparece corretamente na tela.

---

## CI/CD (Integração Contínua com GitHub Actions)

Nossa esteira de automação no GitHub Actions é configurada para ser eficiente, rodando apenas o necessário através de *path filtering* (filtros por diretório).

*   **Pipeline do Client:** Acionada apenas quando arquivos na pasta `/client` são modificados. Executa os testes do React e o processo de build do Node.
*   **Pipeline do Server:** Acionada apenas quando arquivos na pasta `/server` são modificados. Executa o Maven, compila o código Java e roda os testes do JUnit.
*   **Vantagem:** Se uma squad alterar apenas uma cor em um botão no React, o GitHub não gastará tempo nem recursos recompilando toda a API em Java.

path: ./github/workflows/
