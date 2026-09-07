# PsiUnisantos - Frontend

Aplicação desenvolvida em **React, TypeScript e Vite**, focada em alta performance, manutenibilidade e total alinhamento com padrões open-source modernos, utilizando **Bun** como gerenciador de pacotes.

## Tecnologias e Arquitetura

* **Framework:** React com TypeScript
* **Ferramenta de Build:** Vite (com HMR ativado)
* **Gerenciador de Pacotes:** Bun (Anthropic)
* **Estilização e Componentes:** Material UI (Google)
* **Testes End-to-End (E2E):** Playwright

---

## Como Rodar o Projeto Localmente

Certifique-se de ter o [Bun](https://bun.sh/) instalado em sua máquina antes de prosseguir.

### 1. Instalar as Dependências

Clone o repositório e execute a instalação dos pacotes utilizando o Bun:

```bash
bun install

```

### 2. Configurar as Variáveis de Ambiente

Crie um arquivo `.env` na raiz do projeto frontend baseando-se nas configurações necessárias para conectar com o backend:

```env
VITE_API_URL=http://localhost:8080

```

### 3. Iniciar o Servidor de Desenvolvimento

Inicie o ambiente de desenvolvimento local com suporte a Hot Module Replacement (HMR):

```bash
bun dev

```

A aplicação estará acessível em `http://localhost:5173`.

---

## Comandos Úteis

* **Gerar build de produção:**
```bash
bun run build

```


* **Executar testes E2E com Playwright:**
```bash
bun run test:e2e

```


* **Abrir a interface gráfica do Playwright:**
```bash
bun run test:e2e:ui

```