# Infraestrutura e Deploy — PsiUnisantos (clinica-app)

Este documento detalha, passo a passo, toda a configuração de infraestrutura por trás
do pipeline de CI/CD deste repositório (`test.yml` e `deploy.yml`). O objetivo é que
qualquer pessoa da equipe consiga entender, reproduzir ou dar manutenção nessa
configuração sem precisar redescobrir tudo do zero.

## Visão geral do fluxo

```
git push (branch main)
        │
        ▼
GitHub Actions (test.yml → deploy.yml)
        │  conecta via SSH usando a chave em ORACLE_SSH_KEY
        ▼
Oracle VM
        │  git pull (usando deploy key própria da VM)
        │  docker compose up --build -d
        ▼
Containers: clinica-frontend, clinica-backend, clinica-mysql
        │  escutam apenas em 127.0.0.1 (não expostos publicamente)
        ▼
Apache (httpd) na própria VM, escutando em 80/443
        │  faz proxy reverso por subdomínio
        ▼
Internet (via Cloudflare DNS) → <subdominio-frontend> / <subdominio-backend>
```

A ideia central: **os containers Docker nunca ficam expostos diretamente para a
internet**. Só o Apache escuta nas portas públicas (80/443), e ele é quem decide,
com base no subdomínio acessado, para qual container encaminhar a requisição
internamente via `localhost`.

---

## 1. Oracle Cloud — Security List (Ingress Rules)

A Security List é o firewall gerenciado pela própria nuvem Oracle, separado do
firewall interno da VM. Ela controla o que pode chegar até a VM antes mesmo do
sistema operacional processar o pacote.

**Caminho:** Networking → Virtual Cloud Networks → `portfolio-network` → Security Lists

Regras necessárias (Ingress):

| Source | Protocolo | Porta | Motivo |
|---|---|---|---|
| `0.0.0.0/0` | TCP | 22 | Acesso SSH à VM |
| `0.0.0.0/0` | TCP | 80 | HTTP (Apache) |
| `0.0.0.0/0` | TCP | 443 | HTTPS (Apache) |

> **Não é necessário abrir as portas 3000 ou 8080 aqui.** Os containers do
> Docker só escutam em `127.0.0.1`, então mesmo que essas portas estivessem
> liberadas na Security List, não haveria nada acessível externamente nelas —
> é o Apache, escutando em 80/443, quem repassa o tráfego internamente.

## 2. Firewall interno da VM (`firewalld`)

Oracle Linux vem com o `firewalld` ativo por padrão, bloqueando tudo exceto SSH.
Precisa liberar 80 e 443 também nesse firewall (além da Security List — os dois
firewalls existem em camadas diferentes e ambos precisam permitir a porta):

```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

Confirme o que está liberado:

```bash
sudo firewall-cmd --list-all
```

> Se em algum momento as portas 3000/8080 foram abertas por engano
> (`firewall-cmd --add-port=3000/tcp`), pode reverter com `--remove-port`
> — elas não são necessárias nessa arquitetura.

## 3. Portas no `docker-compose.yml`

Os serviços `client` e `server` publicam as portas usando o prefixo `127.0.0.1:`:

```yaml
client:
  ports:
    - "127.0.0.1:3000:80"

server:
  ports:
    - "127.0.0.1:8080:8080"
```

Isso restringe as portas para aceitar conexões **apenas vindas da própria
máquina** (loopback) — nunca de fora. É assim que o Apache (que roda direto no
sistema operacional da VM, fora do Docker) consegue alcançar os containers via
`localhost:3000` e `localhost:8080`, enquanto ninguém de fora da VM consegue
acessar essas portas diretamente pelo IP público.

## 4. Configuração do Apache (`httpd`) — proxy reverso por subdomínio

Arquivo: `/etc/httpd/conf.d/clinica-app.conf`

```apache
# Subdomínio Frontend (React)
<VirtualHost *:80>
    ServerName <subdominio-frontend>
    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:3000/
    ProxyPassReverse / http://127.0.0.1:3000/
</VirtualHost>

# Subdomínio Backend (Spring Boot)
<VirtualHost *:80>
    ServerName <subdominio-backend>
    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/
</VirtualHost>
```

Pré-requisito: os módulos de proxy do Apache precisam estar ativos.

```bash
sudo httpd -M | grep proxy
```

Deve listar `proxy_module` e `proxy_http_module`. Se não aparecerem, instale
`mod_ssl` (`sudo dnf install mod_ssl`) e confirme que as linhas `LoadModule`
correspondentes estão descomentadas em `/etc/httpd/conf.modules.d/00-proxy.conf`.

Depois de qualquer alteração nesse arquivo:

```bash
sudo systemctl restart httpd
```

## 5. Registros DNS na Cloudflare

Na Cloudflare, dentro da zona do domínio `<seu-dominio>`, crie dois registros
tipo **A** apontando para o IP público da VM:

| Tipo | Nome | Conteúdo (IP) | Proxy status |
|---|---|---|---|
| A | `clinica-app` | IP público da VM | conforme preferência da equipe |
| A | `clinica-api` | IP público da VM | conforme preferência da equipe |

> Se o proxy da Cloudflare (ícone de nuvem laranja) estiver ativado, o tráfego
> passa pelos servidores da Cloudflare antes de chegar na VM — isso pode
> interferir na emissão do certificado via Certbot (modo HTTP-01), então é mais
> simples deixar como "DNS only" (nuvem cinza) durante a configuração inicial e
> ativar o proxy depois, se desejado.

Confirme a propagação antes de seguir para o Certbot:

```bash
dig +short <subdominio-frontend>
dig +short <subdominio-backend>
```

Ambos devem retornar o IP público da VM.

## 6. Certificados HTTPS com Certbot

```bash
sudo dnf install certbot python3-certbot-apache -y

sudo certbot --apache -d <subdominio-frontend> -d <subdominio-backend>
```

O Certbot detecta os `VirtualHost *:80` já existentes no `clinica-app.conf`,
gera os certificados, e cria automaticamente os blocos `<VirtualHost *:443>`
correspondentes, reaplicando o mesmo `ProxyPass`/`ProxyPassReverse` de cada
subdomínio.

> **Se a VM também hospeda outro site (ex: WordPress) com certificado próprio**,
> sem um `VirtualHost *:443` dedicado para os subdomínios da clínica, o Apache
> usa o certificado/vhost padrão (geralmente o do outro site) para qualquer
> acesso HTTPS sem correspondência exata — por isso o Certbot precisa ser
> rodado especificamente para esses dois subdomínios antes de testar via
> `https://`.

Os certificados renovam automaticamente via `certbot.timer` (systemd), que já
vem configurado pela instalação do pacote. Para testar a renovação manualmente:

```bash
sudo certbot renew --dry-run
```

## 7. Variáveis e Secrets no GitHub

**Caminho:** repositório → Settings → Environments → `production`

O nome do environment precisa bater exatamente com o usado no `deploy.yml`
(`environment: production`).

### Environment secrets (sensíveis)

| Secret | Valor |
|---|---|
| `ORACLE_HOST` | IP público da Oracle VM |
| `ORACLE_USERNAME` | `opc` (usuário padrão do Oracle Linux) |
| `ORACLE_SSH_KEY` | chave **privada** dedicada ao GitHub Actions (ver seção 8) |
| `ORACLE_SSH_PORT` | `22` |

> Essas credenciais são usadas pelo GitHub Actions para **entrar** na VM via
> SSH e disparar o deploy — não confundir com a chave da seção 9, que serve
> para a VM **buscar código** do GitHub.

### Variáveis de ambiente da aplicação (`.env` na VM)

Essas **não** ficam no GitHub — vivem apenas no arquivo `.env` dentro de
`~/clinica-app` na própria VM, lido pelo `docker-compose.yml` no momento do
`docker compose up`:

```
MYSQL_DATABASE=...
MYSQL_USER=...
MYSQL_PASSWORD=...
MYSQL_ROOT_PASSWORD=...
GRAFANA_ADMIN_PASSWORD=...
VITE_API_URL=https://<subdominio-backend>
```

> Gere senhas fortes com `openssl rand -base64 24`. Sem esse arquivo, o
> container do MySQL inicializa com credenciais vazias e o healthcheck do
> `docker-compose.yml` falha indefinidamente, travando o `server` (que depende
> do MySQL estar saudável para subir).

## 8. Chave SSH para o GitHub Actions acessar a VM

Gerada uma única vez, na própria VM:

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github_deploy_key
```

Sem passphrase (deixe em branco nas duas perguntas), para permitir uso não
interativo pelo workflow.

Autorize essa chave a logar na própria VM:

```bash
cat ~/.ssh/github_deploy_key.pub >> ~/.ssh/authorized_keys
```

Copie a chave **privada** e cole no secret `ORACLE_SSH_KEY` (seção 7):

```bash
cat ~/.ssh/github_deploy_key
```

## 9. Chave SSH para a VM buscar código do GitHub (`git pull`)

Direção oposta da chave anterior — essa permite que a **VM** se autentique no
**GitHub** para clonar/atualizar o repositório, sem depender de usuário/senha.

```bash
ssh-keygen -t ed25519 -C "oracle-vm-git" -f ~/.ssh/id_ed25519_git
```

Sem passphrase. Copie a chave pública:

```bash
cat ~/.ssh/id_ed25519_git.pub
```

Cole em: repositório → Settings → **Deploy keys** → Add deploy key. Deixe
**"Allow write access" desmarcado** — a VM só precisa ler o repositório.

Configure o SSH da VM para usar essa chave especificamente com o GitHub:

```bash
cat >> ~/.ssh/config << 'EOF'
Host github.com
    IdentityFile ~/.ssh/id_ed25519_git
    IdentitiesOnly yes
EOF
```

> Isso não interfere no seu acesso pessoal à VM — esse arquivo `~/.ssh/config`
> só controla conexões **saindo** da VM para outros servidores, nunca conexões
> chegando na VM.

Clone o repositório (uma única vez, manualmente) usando a URL SSH:

```bash
git clone git@github.com:pce-clinica-psicologia/clinica-app.git ~/clinica-app
```

Teste antes de confiar no workflow automático:

```bash
cd ~/clinica-app && git pull origin main
```

Se não pedir usuário/senha, o `deploy.yml` vai funcionar da mesma forma, pois
ele executa exatamente esse comando via SSH.

## 10. Como pegar a senha gerada pelo Spring Security

Enquanto nenhuma configuração de autenticação customizada existir no backend,
o Spring Security gera uma senha aleatória a cada vez que o container reinicia,
e a exibe no log. Para consultar em produção:

```bash
docker logs clinica-backend | grep "Using generated security password"
```

Use o usuário `user` e o UUID retornado para acessar qualquer endpoint
protegido (Swagger, Actuator, etc.) enquanto esse mecanismo temporário estiver
em uso.

> **Atenção:** essa senha muda a cada restart do container e não representa
> nenhum controle de acesso real (RBAC). Ela deve ser substituída por um
> `SecurityConfig` com autenticação de verdade antes de qualquer uso com dados
> reais de pacientes, em conformidade com os requisitos de sigilo do projeto
> (RNF03, RNF06, seção 10 do documento de requisitos).

## 11. Persistência de dados do MySQL entre deploys

O `docker-compose.yml` usa um volume nomeado (`mysql_data`) para o serviço
`mysql`. Isso garante que os dados **sobrevivem** a cada novo deploy:

| Comando | Efeito sobre o volume |
|---|---|
| `docker compose up --build -d` (usado pelo `deploy.yml`) | Preserva os dados — só reconstrói as imagens da aplicação |
| `docker compose down` (sem flag) | Preserva os dados — só derruba os containers |
| `docker compose down -v` | **Apaga os dados** — usar apenas em ambiente sem dados reais, nunca em produção com pacientes cadastrados |

> ⚠️ Depois que existirem dados reais de pacientes no sistema, o comando
> `docker compose down -v` (ou `docker volume rm`) nunca deve ser executado
> sem um backup prévio, sob risco de perda permanente — o que violaria
> diretamente o RNF12 do documento de requisitos (retenção de registros por
> no mínimo 20 anos).

## 12. Troubleshooting rápido

| Sintoma | Causa provável | Onde olhar |
|---|---|---|
| `missing server host` no Actions | Secret `ORACLE_HOST` vazio ou environment com nome errado | Settings → Environments → `production` |
| `dependency mysql failed to start` | `.env` ausente ou com senha vazia na VM | `cat ~/clinica-app/.env` |
| HTTPS mostra outro site (ex: WordPress) | Certbot não rodado para os subdomínios da clínica | Seção 6 deste documento |
| `localhost refused to connect` / redirect quebrado com porta duplicada | Falta `server.forward-headers-strategy` no `application.properties` | `src/main/resources/application.properties` |
| `git pull` pede senha na VM | Deploy key não configurada ou `~/.ssh/config` ausente | Seção 9 deste documento |
