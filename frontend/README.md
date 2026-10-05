# psychology-web — frontend

Frontend do consultório — a interface do psicólogo autônomo para a
[API](../backend/README.md), no mesmo repositório: painel do dia, agenda da
semana, cadastro e ficha de paciente, e o registro da anotação de cada sessão.

> **Status:** as telas estão de pé e rodam ponta a ponta contra a API real, e os
> seis fluxos de e2e passam contra ela. Em produção o front é servido pelo Caddy da
> VM, na mesma origem da API (`/api`); o deploy está pronto no repositório e **não foi
> executado** — veja [`docs/operacao/implantacao.md`](../docs/operacao/implantacao.md).

> **Este arquivo descreve o código como ele está**, e não o plano que o gerou.
> Onde o [design](../docs/superpowers/specs/2026-08-26-frontend-design.md)
> divergir do que está escrito aqui, quem tem razão é o código — as divergências
> conhecidas estão listadas em [O que a spec pede e ainda não
> existe](#o-que-a-spec-pede-e-ainda-não-existe).

## Telas

| Rota | O que faz |
|---|---|
| `/entrar` | entrar. Sessão expirada volta para cá com `Sua sessão expirou. Entre novamente.` e, depois de entrar, devolve à página em que estava |
| `/cadastro` | pública. `Código de convite`, `Nome`, `E-mail`, `Senha`, a caixa `Li e aceito os Termos de Uso e a Política de Privacidade` e `Criar conta`. Criada a conta, o front chama `POST /auth/login` com o mesmo e-mail e senha e entra direto. `503` mostra `Cadastro ainda não está disponível.` |
| `/termos`, `/privacidade` | públicas. O texto vem de `GET /termos/atual`, mostrado como parágrafos (nenhum HTML do arquivo é interpretado) |
| `/` | Hoje: próxima sessão em destaque, as sessões do dia com um clique para marcar *Realizada*/*Faltou*, as pendências do dia e o modo privacidade |
| `/agenda` | grade da semana de segunda a domingo, nas horas do expediente dos Ajustes (sem expediente, 07h–19h; sessão fora dele estica a grade). Clicar numa célula abre o diálogo de nova consulta, com checagem prévia de conflito |
| `/pacientes` | lista com busca por nome e o diálogo de cadastro |
| `/pacientes/:pacienteId` | ficha de **página única**: cabeçalho com nome/idade/contato/valor, botão *Editar*, linha do tempo das sessões com a anotação dentro da linha de cada uma, e as pendências ao lado |
| `/financeiro` | o mês: painel de presença, faturado/recebido/a receber, quem está devendo (com baixa em lote por paciente) e as sessões do mês, com baixa, desfazer e valor da sessão |
| `/ajustes` | perfil e Trocar senha (diálogo), valor padrão da sessão, Expediente, antecedência do aviso, frases do e-mail, título profissional, resumo diário, a prévia de como o aviso chega e a Trilha de acesso (filtro por tipo, paginada) |
| `/descadastro/:token` | página pública, fora do layout e do login: abre pelo link do e-mail e confirma que o paciente parou de receber lembretes |

**A ficha é uma página só, de propósito.** Ela substituiu as quatro abas
(`/pacientes/:id/anotacoes`, `/lembretes`, `/consultas`) do desenho anterior:
correlacionar "qual anotação é de qual sessão" no cliente era o defeito que a
fusão existe para matar. Uma chamada — `GET /pacientes/{id}/ficha` — devolve a
tela inteira já correlacionada.

**Filtro e página vivem em `useState`, não na URL.** O desenho original pedia
`useSearchParams`, e isso ainda é o alvo; hoje nenhuma tela usa. O preço está
pago e é conhecido: recarregar perde a busca, e o link da busca não é
compartilhável.

## Estrutura

```
src/
├── api/
│   ├── cliente.ts       `api<T>(caminho, opcoes)` — o único lugar com `fetch`.
│   │                    Guarda o access token em memória do módulo, manda
│   │                    `Authorization` e `credentials: 'include'`,
│   │                    prefixa o caminho com `VITE_API_URL`, renova a sessão
│   │                    no `401` e transforma o envelope de erro em `ErroDaApi`.
│   ├── erro.ts          `ErroDaApi` + `erroDoCampo(erro, campo)`
│   ├── data.ts          `paraApi(Date)` / `deApi(string)` — hora local, sem `Z`
│   └── contrato.ts      GERADO do OpenAPI. Não edite à mão (ver abaixo)
├── telas/
│   ├── login/           LoginPage · esquema.ts
│   ├── cadastro/        CadastroPage · esquema.ts
│   ├── termos/          TermosPage · queries.ts
│   ├── hoje/            HojePage · queries.ts
│   ├── agenda/          AgendaPage · DialogoDeConsulta · esquema.ts · queries.ts
│   ├── pacientes/       PacientesPage · DialogoDePaciente · esquema.ts · queries.ts
│   └── ficha/           FichaPage · LinhaDoTempo · RegistroDaSessao · queries.ts
├── formulario/          useFormularioDoProduto · ResumoDeErros
├── navegacao/           guardaDeSaida.ts — a guarda de rascunho não enviado
├── sessao/              SessaoProvider · useSessao · RotaProtegida
├── ui/                  Layout · Navegacao · Botao · Campo · Chip · Cartao · EstadoVazio
├── estilo/              tokens.css · base.css · estilo.css · dialogo.css
└── teste/               setup · servidor MSW · renderizar()
```

Não existe `src/features/` nem `src/api/recursos/` — o diretório das telas
chama-se `telas/`, e cada tela leva o próprio `queries.ts` ao lado.

### Os tipos da API são gerados, não escritos

`src/api/contrato.ts` sai do `contrato-openapi.json` (a cópia do
`/v3/api-docs` da API) por `openapi-typescript`:

```bash
npm run tipos
```

Os `queries.ts` **não** exportam o tipo gerado direto: cada um escreve um
container à mão e tira só o tipo de cada campo do contrato
(`NonNullable<PacienteDTO['nome']>`). O springdoc só sabe dizer "opcional",
nunca "nulável de verdade", e `Required<>` é raso — o container à mão é o que
distingue "o backend pode mandar null aqui" de "o gerador não soube dizer". A
amarra continua valendo: renomear um campo no backend quebra o build aqui.

### O caminho de um clique

Seguindo o botão "Registrar" da caixa de anotação:

```
RegistroDaSessao.tsx                    telas/ficha/
  │  componente. Estado de tela e chamada de hook — não monta
  │  URL, não conhece `fetch`, não trata `Response`.
  ▼
useRegistrarAnotacao(pacienteId)        telas/ficha/queries.ts
  │  a mutation, e as invalidações declaradas AQUI:
  │  onSuccess → invalida ['ficha', pacienteId] e ['painel','hoje'].
  │  A segunda porque registrar a anotação resolve a pendência
  │  SEM_ANOTACAO que a tela Hoje está mostrando.
  ▼
api<T>('/pacientes/{id}/anotacoes', {method:'POST', ...})   api/cliente.ts
     Authorization com o token em memória, `credentials: 'include'`,
     e o envelope `{status, mensagem, erros[]}` virando `ErroDaApi`.
```

**Invalidação mora no `queries.ts`.** Toda escrita derruba as chaves que
afeta, declaradas no `onSuccess` — e não se conta com o conserto que acontece
sozinho no `refetchOnMount`, porque o que se conserta sozinho hoje deixa de se
consertar no dia em que alguém mexer no `staleTime`. As chaves são
`['pacientes']`, `['ficha', pacienteId]`, `['painel','hoje']`, `['consultas',
…]` e `['conflito', …]`. Toda escrita de dinheiro e a troca de status derrubam
os três prefixos `['financeiro']`, `['painel']` (cobre `hoje` e `mes`) e
`['ficha']`.

**Uma exceção conhecida:** `telas/agenda/DialogoDeConsulta.tsx` chama
`api('/consultas', …)` e `invalidateQueries` direto no componente, em vez de
passar por um hook do `queries.ts` como as outras três escritas. É o único
lugar onde a fronteira está furada.

**A trilha só aparece nos Ajustes.** A seção Trilha de acesso lê `GET /auditoria`; a leitura não é auditada. Não há tela `/auditoria` própria.

## Formulários

Todos passam por `useFormularioDoProduto` — react-hook-form com `zodResolver`,
mais três regras fixas:

```
mode: 'onTouched'            valida no blur, só depois do primeiro toque
reValidateMode: 'onChange'   depois que errou, revalida a cada tecla
shouldFocusError: true       submit inválido põe foco no primeiro campo inválido
```

O design original **rejeitava** validar no cliente, para não ter duas fontes de
verdade. O que existe hoje é a versão conciliada: o zod recusa o que dá para
recusar sem ir ao servidor, e **o texto do servidor vence o do zod** quando os
dois falam do mesmo campo — o servidor conhece regras que o cliente não tem como
checar (conflito de horário, canal de aviso sem e-mail). É `enviar()` que faz
isso, transformando cada `erros[].campo` do envelope num erro daquele campo.

Três convenções que valem em toda tela:

- **Obrigatoriedade é visível antes do erro.** `ui/Campo.tsx` escreve `*` no
  campo obrigatório e `(opcional)` no resto; o `<textarea>` da anotação, que é
  escrito à mão porque `Campo` desenha `<input>`, repete a mesma marca.
- **Mensagem de erro é frase inteira e acentuada** ("Informe o conteúdo da
  anotação."), a mesma do servidor quando há uma.
- **Envio é estado, não evento.** O botão trava enquanto a chamada está em voo.

## Sessão

O access token vive **apenas em memória do módulo** (`api/cliente.ts`) — nunca
em `localStorage`. Num produto com prontuário, token em `localStorage`
transforma qualquer XSS em vazamento de prontuário.

**A sessão é renovada no boot e no `401`.**

1. **Boot.** Antes de qualquer rota protegida, o `SessaoProvider` chama
   `POST /auth/refresh`. `200`: a sessão volta sem login — é assim que o F5 não desloga
   sem que exista token guardado no navegador. `401`: vai para `/entrar`. Enquanto
   renova, a tela mostra `Carregando…`.
2. **`401` no meio do uso.** Todas as chamadas que falham juntas compartilham **uma**
   promise de renovação, e a API nunca vê o mesmo refresh duas vezes (ela rotaciona o
   refresh a cada uso e leria a repetição como token roubado). Renovou: cada chamada
   original é repetida uma vez. Recusada (`401`/`403` do refresh): limpa o token e o
   cache do TanStack, vai para `/entrar` com `Sua sessão expirou. Entre novamente.` e,
   depois de entrar, volta à rota em que estava. O que estava digitado num formulário
   aberto se perde.
3. **Falha que não derruba a sessão.** Rede fora ou `5xx` na renovação mantém o token e
   não manda ao login: a chamada original falha com o `401` original e a próxima tenta de novo.
4. **O que nunca renova.** `/auth/*` e `/descadastro/*`.

O prefixo das chamadas vem de `VITE_API_URL`: `/api` em produção (o Caddy tira o prefixo
antes da API), vazio em desenvolvimento e nos testes.

O nome de quem está logado sai do claim `sub` do próprio JWT — a API não tem
rota que devolva o usuário logado. Ler um claim de um token que já é nosso é
legítimo; o que seria errado é *confiar* nele para autorizar.

`Sair` chama `POST /auth/logout` e derruba a sessão local **mesmo se a chamada
falhar**. Ficar preso logado por causa de um erro de rede, num consultório com
terceiro na sala, é pior do que o servidor não saber que a sessão acabou.

## Rascunho de anotação

O texto da anotação vive **só em memória** — nem `sessionStorage` nem
`localStorage`. Essa decisão só é honesta com a guarda junto, senão "em
memória" vira "perde em silêncio": `src/navegacao/guardaDeSaida.ts` registra um
`beforeunload` enquanto houver rascunho sujo, e os links internos perguntam
antes de navegar.

Não é `useBlocker` do React Router: ele exige um data router
(`createBrowserRouter`), e este app monta `<BrowserRouter>` + `<Routes>`. Trocar
a raiz do roteamento por causa de uma caixa de texto era caro demais.

## Sistema visual

Os tokens ficam em `src/estilo/tokens.css` e são a única fonte de cor do
produto — nenhum componente escreve hex. A linguagem é a do Google Calendar, e a
regra que a sustenta é **ação é forma, não cor**: pilula = ação (botão, item
ativo da navegação), bloco chapado com barra à esquerda = evento. É o que
permite botão e consulta serem os dois azuis sem confundir.

`src/estilo/estilo.css` é em boa parte cópia verbatim de
`../prototipo/estilo.css`; onde o DOM real difere do protótipo (que era HTML
solto com ids fixos, e aqui são componentes React guiados pelos testes), a regra
foi reescrita para o mesmo resultado visual, e isso está marcado nos
comentários.

`Chip` (`src/ui/Chip.tsx`) é o rótulo de estado, e a variante é que dá a cor
semântica: `realizada` verde, `faltou` e `alerta` âmbar, neutro cinza. Um status
tem **uma** cor no produto inteiro — desenhar `<span className="chip">` cru na
mão é o que fazia REALIZADA sair verde na tela Hoje e cinza na ficha.

Duas coisas do desenho de referência **não** existem aqui porque não existem na
API: "Esqueci minha senha" (não há endpoint de recuperação) e "Observações" no
cadastro de paciente.

Divergência conhecida: `index.html` ainda carrega Inter do Google Fonts, mas
`tokens.css` importa e usa **Roboto** (`--fonte`). O `<link>` do Inter é peso
morto.

## Endpoints que este cliente usa

| Verbo | Caminho | Quem chama |
|---|---|---|
| `POST` | `/auth/login` | `sessao/SessaoProvider.tsx` |
| `POST` | `/auth/refresh` | `api/cliente.ts` (boot e `401`) |
| `POST` | `/auth/registrar` | `telas/cadastro/CadastroPage.tsx` |
| `GET` | `/termos/atual` | `telas/termos/queries.ts` (e a versão enviada no cadastro) |
| `POST` | `/auth/logout` | `sessao/SessaoProvider.tsx` |
| `GET` | `/painel/hoje` | `telas/hoje/queries.ts` |
| `PATCH` | `/consultas/{id}/status` | `telas/hoje/queries.ts` |
| `GET` | `/financeiro/mes?ano&mes` | `telas/financeiro/queries.ts` |
| `GET` | `/painel/mes?ano&mes` | `telas/financeiro/queries.ts` |
| `PATCH` / `DELETE` | `/consultas/{id}/baixa` | `telas/financeiro/queries.ts` (usado também pela Hoje e pela ficha) |
| `POST` | `/consultas/baixas` | `telas/financeiro/queries.ts` |
| `PATCH` | `/consultas/{id}/valor` | `telas/financeiro/queries.ts` |
| `GET` | `/consultas?de&ate` | `telas/agenda/queries.ts` |
| `GET` | `/consultas/conflito?dtConsulta&excluirConsultaId` | `telas/agenda/queries.ts` |
| `POST` | `/consultas` | `telas/agenda/DialogoDeConsulta.tsx` |
| `GET` | `/pacientes?nome` | `telas/pacientes/queries.ts` |
| `POST` / `PUT` | `/pacientes`, `/pacientes/{id}` | `telas/pacientes/queries.ts` |
| `GET` | `/pacientes/{id}/ficha` | `telas/ficha/queries.ts` |
| `POST` | `/pacientes/{id}/anotacoes` | `telas/ficha/queries.ts` |
| `GET` / `PUT` | `/ajustes` | `telas/ajustes/queries.ts` |
| `POST` | `/ajustes/senha` | `telas/ajustes/senha.ts` |
| `GET` | `/auditoria?entidade&page` | `telas/ajustes/trilha.ts` |
| `GET` / `POST` | `/descadastro/{token}` | `telas/descadastro/queries.ts` |

Série recorrente, lembretes e exclusão de paciente
existem na API e **não** têm cliente aqui.

### Campo em branco no `PUT /pacientes/{id}`

A regra é do servidor e o cliente a respeita literalmente: **campo enviado em
branco (`""`) LIMPA; campo ausente do payload fica intocado.** Vale para `email`
e `telefone`.

Por isso o mapeamento de campo vazio é **diferente** nos dois métodos
(`telas/pacientes/queries.ts`):

- `POST` — vazio vira `null`. Não há o que limpar num cadastro novo.
- `PUT` — `email` e `telefone` vão como estão, inclusive `""`. O diálogo sempre
  abre com o valor atual preenchido, então o que sobrou na caixa é a intenção de
  quem editou; mandar `null` ali faria o "Salvar" não fazer nada visível.

Limpar o e-mail com `canalAviso = EMAIL` é recusado pelo servidor com `400` e
mensagem acionável, que chega ao campo pelo envelope de sempre.

## Subindo o conjunto

O frontend não serve para nada sozinho — ele não tem dados. A ordem é
banco → API → web. As duas aplicações vivem neste mesmo repositório; o
[README da raiz](../README.md) traz a mesma receita do ponto de vista do monorepo.

### 1. Banco

PostgreSQL em `localhost:5432` com uma database chamada `psychology-api`. O
`createdb` é um programa do PostgreSQL, não do projeto: roda em qualquer terminal,
de qualquer diretório.

```bash
createdb psychology-api
```

O banco fica **vazio** — as migrations do Flyway rodam sozinhas no boot da API, e
não há passo manual de schema. Numa máquina nova, o passo a passo completo (papel
do Postgres, `JWT_SECRET`, Java 21) está em
[`backend/README.md`](../backend/README.md#máquina-nova-do-zero).

### 2. API

Em `backend/`:

```bash
cd ../backend
./debug.sh
```

O script carrega o arquivo de ambiente local (não versionado), aponta o
datasource para o Postgres da máquina e sobe o Spring na porta 8080.

**Requisitos:** Java 21 e Docker (o Docker só é preciso para rodar os testes da
API, que usam Testcontainers — para subir a aplicação, não).

Sem `JWT_SECRET` a aplicação **não sobe**, de propósito: falhar no boot é melhor
que subir com segredo default.

### 3. Web

De volta em `frontend/`:

```bash
npm install
npm run dev
```

Abre em <http://localhost:5173>. A conta se cria em `/cadastro`, que exige um código de
convite e os textos dos termos instalados na API. Em desenvolvimento, no ambiente da API,
defina `APP_CADASTRO_CONVITES=convite-local` e `APP_TERMOS_DIRETORIO=file:/caminho/de/uma/pasta/`
com um `termos.md` e um `privacidade.md` de desenvolvimento, cada um com a primeira linha
`versao: dev` (o texto jurídico de verdade só entra com o advogado). Sem eles, o cadastro
mostra `Cadastro ainda não está disponível.` ou `Código de convite inválido.`

Cada conta enxerga só os próprios pacientes.

**Requisitos:** Node 20 ou mais novo. Com Node antigo o `npm install` quebra com
`Cannot find module 'node:path'` — é o Vite 8 pedindo runtime moderno. Confira com
`node -v`.

Se a API estiver em outra porta (a 8080 nem sempre está livre):

```bash
API_URL=http://localhost:8090 npm run dev
```

### O que dá errado quando se pula um passo

O front não avisa "faltou subir a API" — ele mostra o sintoma de cada camada que
faltou. Vale reconhecer os três:

| O que você vê | O que faltou |
|---|---|
| Tela de login com "não foi possível conectar ao servidor" | a API não está no ar. O front distingue isso de credencial inválida de propósito |
| Login aceita, mas toda tela fica vazia e o console mostra `401` | a API subiu, mas com outro `JWT_SECRET` do que o token que o navegador tinha. Entre de novo |
| A API não sobe, `database "psychology-api" does not exist` | faltou o `createdb`. Veja o passo 1 |

## Variáveis de ambiente

**Da API** (todas lidas em `application.properties`):

| Variável | Padrão | Para que serve |
|---|---|---|
| `SPRING_DATASOURCE_URL` | — | `jdbc:postgresql://localhost:5432/psychology-api` |
| `SPRING_DATASOURCE_USERNAME` | — | usuário do Postgres |
| `SPRING_DATASOURCE_PASSWORD` | — | senha do Postgres |
| `JWT_SECRET` | — | **obrigatória.** Segredo do HMAC-SHA256, em Base64 |
| `PORT` | `8080` | porta do Spring |
| `APP_CORS_ORIGINS` | `http://localhost:5173` | origem liberada com credenciais |
| `APP_REFRESH_EXPIRACAO_DIAS` | `14` | validade do refresh token |
| `APP_REFRESH_COOKIE_SEGURO` | `true` | `Secure` no cookie de refresh |
| `APP_REFRESH_COOKIE_SAMESITE` | `None` | `SameSite` do cookie de refresh. `Strict` em produção |
| `APP_REFRESH_COOKIE_PATH` | `/auth` | `Path` do cookie de refresh. `/api/auth` em produção |
| `APP_TERMOS_DIRETORIO` | `classpath:termos/` | pasta de `termos.md` e `privacidade.md` |
| `APP_CADASTRO_CONVITES` | vazio | códigos de convite separados por vírgula |
| `APP_LIMITE_POR_MINUTO` | `10` | tentativas por minuto por IP e por rota em login, cadastro e refresh |
| `APP_EMAIL_REMETENTE` | `lembretes@localhost` | endereço de origem dos e-mails de aviso |
| `APP_FRONTEND_URL` | `http://localhost:5173` | origem do frontend usada no link de descadastro do e-mail |
| `APP_AVISOS_HABILITADO` | `true` | liga o job de avisos; `false` o desliga (use em teste e em segunda instância) |
| `SPRING_MAIL_HOST` | vazio | servidor SMTP. Vazio: o job de avisos não roda e nada é registrado como enviado. Com host, `APP_EMAIL_REMETENTE` e `APP_FRONTEND_URL` são obrigatórias, ou a API não sobe |
| `SPRING_MAIL_PORT` | `587` | porta do SMTP |
| `SPRING_MAIL_USERNAME` | vazio | usuário do SMTP |
| `SPRING_MAIL_PASSWORD` | vazio | senha do SMTP — só no painel da plataforma, nunca no repositório |
| `SPRING_MAIL_AUTH` | `true` | autenticação SMTP |
| `SPRING_MAIL_STARTTLS` | `true` | STARTTLS no SMTP |

As três primeiras o `debug.sh` já define para o ambiente local. As de cookie, termos, convite
e limite têm padrão de desenvolvimento; a tabela completa de produção está em
[`docs/operacao/implantacao.md`](../docs/operacao/implantacao.md).

**Do web:**

| Variável | Padrão | Para que serve |
|---|---|---|
| `VITE_API_URL` | vazio | prefixo de toda chamada: `src/api/cliente.ts` monta `${VITE_API_URL ?? ''}${caminho}`. Em produção é `/api` (o `infra/caddy/Dockerfile` o fixa no build); em desenvolvimento e nos testes fica vazio, e quem resolve é o proxy do Vite |
| `API_URL` | `http://localhost:8080` | alvo do proxy do Vite. Só serve em desenvolvimento, para quando a 8080 estiver ocupada: `API_URL=http://localhost:8090 npm run dev` |

## Por que o proxy espelha os caminhos

Em desenvolvimento o Vite proxia `/auth`, `/pacientes`, `/consultas`,
`/anotacoes`, `/pendencias`, `/painel`, `/auditoria`, `/financeiro`, `/descadastro`,
`/ajustes`, `/termos` e `/saude` para
`http://localhost:8080`. Isso torna tudo same-origin e tira CORS e `SameSite` do
caminho.

O proxy **espelha os caminhos**, sem prefixo `/api`: em desenvolvimento o cookie de
refresh é marcado `Path=/auth` (o padrão de `APP_REFRESH_COOKIE_PATH`), e sob um prefixo
o navegador veria `/api/auth/refresh` e simplesmente não o enviaria. Em produção o
prefixo existe, e por isso lá o cookie sai com `Path=/api/auth`.

O preço do espelho é que `/pacientes`, `/ajustes`, `/descadastro` e `/termos` são caminho de API **e** rota de tela.
Recarregar a página em `/pacientes` entregava o JSON da API no lugar do app. Daí
o `bypass` no `vite.config.ts`: navegação de documento pede `text/html` e volta
para o `index.html`; chamada do cliente pede JSON e segue para a API.

## Testes

```bash
npm test          # 338 testes de unidade e de componente, com MSW
npm run e2e       # 6 fluxos contra a API real — exigem a API no ar
```

Na primeira vez, o Playwright precisa do navegador:

```bash
npx playwright install chromium
```

Duas convenções valem em toda a suíte, e não são estilo:

- **Navegação e asserção por rótulo escrito, nunca por `className`.**
  `getByRole('button', { name: 'Editar' })`, `getByRole('list', { name:
  'Sessoes' })`. Um seletor de classe quebra numa renomeação puramente visual e
  passa numa tela que perdeu o texto.
- **Os handlers do MSW respondem o contrato real** — o mesmo corpo de sucesso, o
  mesmo envelope `{status, mensagem, erros[]}` de erro e os mesmos códigos HTTP
  que a API devolve — para o teste exercitar o `api/cliente.ts` de verdade, e
  não um mock de módulo.

`e2e/` fica fora do glob do Vitest (`vite.config.ts`), porque o `test()` do
Playwright explode dentro do Vitest.

### Os seis fluxos de e2e

| Arquivo | O que percorre |
|---|---|
| `e2e/fluxo-de-fumaca.spec.ts` | registrar (pela API) → entrar → ver o dia → agendar → marcar presença |
| `e2e/fluxo-da-sessao.spec.ts` | cadastrar paciente → agendar **duas** sessões no mesmo dia → marcar só a primeira como realizada → anotar → conferir que a anotação voltou na linha da sessão A **e não** na da sessão B |
| `e2e/fluxo-do-dinheiro.spec.ts` | cadastrar paciente com valor → agendar hoje → marcar *Realizada* → Financeiro mostra faturado e a receber → dar baixa em todas → recebido → desfazer baixa e o débito volta |
| `e2e/fluxo-do-aviso.spec.ts` | Ajustes: antecedência `2 horas antes` e uma frase ligada → salvar → sair e voltar, os valores persistiram e a prévia mostra a frase → cadastrar paciente com o consentimento marcado → a ficha mostra *Aviso autorizado* |
| `e2e/fluxo-dos-ajustes.spec.ts` | Ajustes: expediente `08:00`–`18:00` → Agenda mostra `08:00` a `17:00` → Trilha de acesso mostra `Alterou os ajustes` → Trocar senha (Cancelar devolve o foco ao botão) → trocar, sair e entrar com a nova |
| `e2e/fluxo-do-cadastro.spec.ts` | `/entrar` → `Criar conta` → cadastro com código de convite e aceite dos termos → entra direto → F5 continua logada → sair |

Os e2e que criam conta usam códigos de convite de teste, passados por variável de
ambiente (`E2E_CONVITES`, separados por vírgula; a API precisa ter os mesmos em
`APP_CADASTRO_CONVITES` e os textos dos termos instalados). Para rodar contra uma pilha
já no ar, `E2E_BASE_URL` aponta a origem e `E2E_API_PREFIX` o prefixo das chamadas à API
(`/api` atrás do Caddy); `ignoreHTTPSErrors` só liga quando a origem é `https://localhost`.
Cada código vale uma vez: a pilha precisa de convites novos a cada execução.

A segunda metade dessa última linha é o ponto. Enquanto o fluxo criava uma
sessão só, "a anotação está na linha certa" era verdade por construção: com uma
linha na ficha, qualquer vínculo que o backend fizesse passava. A asserção
negativa sobre a sessão B é a que dá valor à positiva.

O e2e já provou o valor duas vezes: pegou a tela morta em que o login caía depois
de autenticar, e pegou que este projeto lia `erros[].mensagem` onde a API manda
`erros[].erro` — com todos os mocks errados junto, então a suíte inteira ficava
verde enquanto, contra a API real, um `400` de validação não mostrava nada na
tela.

Cada fluxo cria um **usuário novo por execução** (`*-${Date.now()}@teste.local`,
senha `senhaforte123`): o banco não tem usuário semeado e o cadastro responde
`409` em e-mail repetido. Nenhum dado de pessoa real entra num teste.

## Build

```bash
npm run build     # tsc -b && vite build, saída em dist/
npm run preview   # serve o dist/ para conferir o build
```

`npm test` **não** checa tipos — o Vitest tira os tipos com esbuild. Rode
`npx tsc --noEmit` (ou `npm run build`) antes de considerar uma mudança pronta;
já houve defeito de tipo que ficou verde na suíte e quebrou o build.

Em produção não há proxy do Vite: o build roda com `VITE_API_URL=/api`, e o Caddy da
VM serve o front e repassa `/api/*` à API na mesma origem. Quem gera o build é o
`infra/caddy/Dockerfile` (`npm ci && npm run build`).

## Hospedagem

O front é servido pelo Caddy do `infra/compose.yml`, na mesma origem da API. O Caddy
faz `try_files {path} /index.html`, então recarregar em `/pacientes` ou abrir o link
direto de uma ficha devolve o app em vez de um 404. Ele também manda
`Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy:
no-referrer` (o link de descadastro leva um token na URL) e uma
`Content-Security-Policy` que só libera o próprio domínio e o Google Fonts.

O passo a passo completo, com a checagem que prova cada etapa, está em
[`docs/operacao/implantacao.md`](../docs/operacao/implantacao.md). Na origem publicada,
a checagem do front é: entrar → **F5 em `/pacientes`** (a sessão volta e a rota abre) →
cadastrar paciente → F5 em `/agenda`; e, nas ferramentas do navegador, o cookie de refresh
com `Path=/api/auth`.

`frontend/vercel.json` e `frontend/public/_redirects` ficam no repositório de uma fase
anterior e **não são usados** pela hospedagem atual.

## Quando não sobe

| Sintoma | Causa provável |
|---|---|
| `npm install` falha com `Cannot find module 'node:path'` | Node antigo no PATH. Este projeto pede Node 20 ou mais novo (Vite 8) |
| A API não sobe, `PlaceholderResolutionException` | falta `JWT_SECRET` no ambiente |
| A API não sobe, porta em uso | outra aplicação na 8080. Suba com `PORT=8081` e rode o front com `API_URL=http://localhost:8081 npm run dev` |
| A tela de login diz "Nao foi possivel conectar ao servidor" | a API não está no ar. O front distingue isso de credencial inválida de propósito |
| `npm run e2e` falha logo no `/auth/registrar` | mesma coisa: o E2E **não** sobe a API, só o Vite |
| Recarregar a página desloga | o cookie de refresh não está voltando: em produção, confira `Path=/api/auth` nas ferramentas do navegador; em desenvolvimento, `Path=/auth`. Ver [Sessão](#sessão) |
| Aparece `Sua sessão expirou. Entre novamente.` | a renovação foi recusada (refresh vencido, revogado ou apagado). Entre de novo |
| `/cadastro` responde `Cadastro ainda não está disponível.` | a API está sem os textos dos termos |
| `/cadastro` responde `Código de convite inválido.` | convite desconhecido ou já usado |
| Em produção, recarregar em `/pacientes` dá 404 | o front não está sendo servido pelo Caddy; confira `docs/operacao/implantacao.md` |
| Em produção, toda chamada falha com erro de CORS | `APP_CORS_ORIGINS` não tem a origem exata do frontend (sem barra no fim) |

## O que a spec pede e ainda não existe

Lista curta e honesta do que o
[design](../docs/superpowers/specs/2026-08-26-frontend-design.md) descreve e o
código não faz:

- **Reaceite de termos** quando a versão mudar: quem já tem conta não é chamado a aceitar de novo.
- **Fonte do Google**: a Inter ainda vem do Google Fonts; servir localmente fecha o débito.
- **Código próprio para o `409` de termos**: hoje é detectado pela mensagem do envelope.
- **Link de termos no cadastro** abre na mesma aba e perde o que foi digitado.
- **Duas abas renovando juntas** podem revogar a cadeia de refresh e derrubar uma delas.
- **Filtro e paginação na URL** (`useSearchParams`). Hoje é `useState`.
- **Telas de lembretes, série recorrente e exclusão de paciente.** Os
  endpoints existem na API; o cliente não.
- **`DialogoDeConsulta` fala HTTP direto**, furando a fronteira de camadas que
  vale nas outras três escritas.

## Documentação

- `../ai/Claude.md` — contexto do backend e as convenções da API
- `../docs/superpowers/specs/2026-08-26-frontend-design.md` — design do frontend, alternativas rejeitadas e as correções vindas da execução
- `../docs/superpowers/specs/2026-09-01-redesenho-produto-design.md` — o redesenho que trouxe a tela Hoje e a ficha de página única
- `../prototipo/` — o protótipo aprovado que `src/estilo/` reproduz
</content>
</invoke>
