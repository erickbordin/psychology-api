# psychology-web — frontend

Frontend do consultório — a interface do psicólogo autônomo para a
[API](../backend/README.md), no mesmo repositório: painel do dia, agenda da
semana, cadastro e ficha de paciente, e o registro da anotação de cada sessão.

> **Status:** as telas estão de pé e rodam ponta a ponta contra a API real, e os
> quatro fluxos de e2e passam contra ela. A configuração de host está pronta;
> falta criar o projeto na plataforma.

> **Este arquivo descreve o código como ele está**, e não o plano que o gerou.
> Onde o [design](../docs/superpowers/specs/2026-08-26-frontend-design.md)
> divergir do que está escrito aqui, quem tem razão é o código — as divergências
> conhecidas estão listadas em [O que a spec pede e ainda não
> existe](#o-que-a-spec-pede-e-ainda-não-existe).

## Telas

| Rota | O que faz |
|---|---|
| `/entrar` | entrar. **Não há tela de criar conta** — a conta nasce por `POST /auth/registrar`, que nenhuma tela chama ainda |
| `/` | Hoje: próxima sessão em destaque, as sessões do dia com um clique para marcar *Realizada*/*Faltou*, as pendências do dia e o modo privacidade |
| `/agenda` | grade da semana, 07h–19h, de segunda a domingo. Clicar numa célula abre o diálogo de nova consulta, com checagem prévia de conflito |
| `/pacientes` | lista com busca por nome e o diálogo de cadastro |
| `/pacientes/:pacienteId` | ficha de **página única**: cabeçalho com nome/idade/contato/valor, botão *Editar*, linha do tempo das sessões com a anotação dentro da linha de cada uma, e as pendências ao lado |
| `/financeiro` | o mês: painel de presença, faturado/recebido/a receber, quem está devendo (com baixa em lote por paciente) e as sessões do mês, com baixa, desfazer e valor da sessão |
| `/ajustes` | perfil, valor padrão da sessão, antecedência do aviso, frases do e-mail, título profissional e resumo diário, com a prévia de como o aviso chega |
| `/descadastro/:token` | página pública, fora do layout e do login: abre pelo link do e-mail e confirma que o paciente parou de receber lembretes |

**A ficha é uma página só, de propósito.** Ela substituiu as quatro abas
(`/pacientes/:id/anotacoes`, `/lembretes`, `/consultas`) do desenho anterior:
correlacionar "qual anotação é de qual sessão" no cliente era o defeito que a
fusão existe para matar. Uma chamada — `GET /pacientes/{id}/ficha` — devolve a
tela inteira já correlacionada.

**Filtro e página vivem em `useState`, não na URL.** O desenho original pedia
`useSearchParams`, e isso ainda é o alvo; hoje nenhuma tela usa. O preço está
pago e é conhecido: recarregar perde a busca, e o link da busca não é
compartilhável. Vale menos que parece porque recarregar hoje também desloga
(ver [Sessão](#sessão)).

## Estrutura

```
src/
├── api/
│   ├── cliente.ts       `api<T>(caminho, opcoes)` — o único lugar com `fetch`.
│   │                    Guarda o access token em memória do módulo, manda
│   │                    `Authorization` e `credentials: 'include'`, e
│   │                    transforma o envelope de erro em `ErroDaApi`.
│   ├── erro.ts          `ErroDaApi` + `erroDoCampo(erro, campo)`
│   ├── data.ts          `paraApi(Date)` / `deApi(string)` — hora local, sem `Z`
│   └── contrato.ts      GERADO do OpenAPI. Não edite à mão (ver abaixo)
├── telas/
│   ├── login/           LoginPage · esquema.ts
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

**Não há tela de auditoria.** A API grava a trilha em toda mutação, mas nenhuma
tela a lê — por isso `['auditoria']` não aparece em invalidação nenhuma.

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

**Não existe renovação de token neste código.** Não há `POST /auth/refresh` no
boot, não há retry de `401`, não há promise de refresh compartilhada — o
`api/cliente.ts` simplesmente propaga o erro. A consequência é direta e
assumida: **recarregar a aba desloga**. O cookie `refresh_token` existe do lado
da API, mas nada aqui o usa ainda. É a maior dívida conhecida deste frontend.

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
| `GET` / `POST` | `/descadastro/{token}` | `telas/descadastro/queries.ts` |

Série recorrente, lembretes, exclusão de paciente e a trilha de auditoria
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

Abre em <http://localhost:5173>. A primeira conta precisa ser criada **pela
API**, porque não há tela de cadastro:

```bash
curl -X POST http://localhost:8080/auth/registrar \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Fulana","email":"fulana@exemplo.local","senha":"senhaforte123"}'
```

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
| `APP_REFRESH_COOKIE_SAMESITE` | `None` | `SameSite` do cookie de refresh |
| `APP_EMAIL_REMETENTE` | `lembretes@localhost` | endereço de origem dos e-mails de aviso |
| `APP_FRONTEND_URL` | `http://localhost:5173` | origem do frontend usada no link de descadastro do e-mail |
| `APP_AVISOS_HABILITADO` | `true` | liga o job de avisos; `false` o desliga (use em teste e em segunda instância) |
| `SPRING_MAIL_HOST` | vazio | servidor SMTP. Vazio: o job de avisos não roda e nada é registrado como enviado. Com host, `APP_EMAIL_REMETENTE` e `APP_FRONTEND_URL` são obrigatórias, ou a API não sobe |
| `SPRING_MAIL_PORT` | `587` | porta do SMTP |
| `SPRING_MAIL_USERNAME` | vazio | usuário do SMTP |
| `SPRING_MAIL_PASSWORD` | vazio | senha do SMTP — só no painel da plataforma, nunca no repositório |
| `SPRING_MAIL_AUTH` | `true` | autenticação SMTP |
| `SPRING_MAIL_STARTTLS` | `true` | STARTTLS no SMTP |

As três primeiras o `debug.sh` já define para o ambiente local. As três últimas
só passam a importar quando o frontend usar o refresh — hoje ele não usa.

**Do web:**

| Variável | Padrão | Para que serve |
|---|---|---|
| `VITE_API_URL` | vazio | origem da API em produção, lida no **build**. Em desenvolvimento fica vazia, e quem resolve é o proxy |
| `API_URL` | `http://localhost:8080` | alvo do proxy do Vite. Só serve em desenvolvimento, para quando a 8080 estiver ocupada: `API_URL=http://localhost:8090 npm run dev` |

## Por que o proxy espelha os caminhos

Em desenvolvimento o Vite proxia `/auth`, `/pacientes`, `/consultas`,
`/anotacoes`, `/pendencias`, `/painel`, `/auditoria`, `/financeiro`, `/descadastro` e
`/ajustes` para
`http://localhost:8080`. Isso torna tudo same-origin e tira CORS e `SameSite` do
caminho.

O proxy **espelha os caminhos**, sem prefixo `/api`: o cookie de refresh é
marcado `Path=/auth`, e sob um prefixo o navegador veria `/api/auth/refresh` e
simplesmente não o enviaria. A renovação quebraria só em desenvolvimento — o pior
lugar para descobrir isso.

O preço do espelho é que `/pacientes`, `/ajustes` e `/descadastro` são caminho de API **e** rota de tela.
Recarregar a página em `/pacientes` entregava o JSON da API no lugar do app. Daí
o `bypass` no `vite.config.ts`: navegação de documento pede `text/html` e volta
para o `index.html`; chamada do cliente pede JSON e segue para a API.

## Testes

```bash
npm test          # 213 testes de unidade e de componente, com MSW
npm run e2e       # 4 fluxos contra a API real — exigem a API no ar
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

### Os quatro fluxos de e2e

| Arquivo | O que percorre |
|---|---|
| `e2e/fluxo-de-fumaca.spec.ts` | registrar (pela API) → entrar → ver o dia → agendar → marcar presença |
| `e2e/fluxo-da-sessao.spec.ts` | cadastrar paciente → agendar **duas** sessões no mesmo dia → marcar só a primeira como realizada → anotar → conferir que a anotação voltou na linha da sessão A **e não** na da sessão B |
| `e2e/fluxo-do-dinheiro.spec.ts` | cadastrar paciente com valor → agendar hoje → marcar *Realizada* → Financeiro mostra faturado e a receber → dar baixa em todas → recebido → desfazer baixa e o débito volta |
| `e2e/fluxo-do-aviso.spec.ts` | Ajustes: antecedência `2 horas antes` e uma frase ligada → salvar → sair e voltar, os valores persistiram e a prévia mostra a frase → cadastrar paciente com o consentimento marcado → a ficha mostra *Aviso autorizado* |

A segunda metade dessa última linha é o ponto. Enquanto o fluxo criava uma
sessão só, "a anotação está na linha certa" era verdade por construção: com uma
linha na ficha, qualquer vínculo que o backend fizesse passava. A asserção
negativa sobre a sessão B é a que dá valor à positiva.

O e2e já provou o valor duas vezes: pegou a tela morta em que o login caía depois
de autenticar, e pegou que este projeto lia `erros[].mensagem` onde a API manda
`erros[].erro` — com todos os mocks errados junto, então a suíte inteira ficava
verde enquanto, contra a API real, um `400` de validação não mostrava nada na
tela.

Os quatro criam um **usuário novo por execução** (`*-${Date.now()}@teste.local`,
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

Em produção não há proxy: origem própria, `VITE_API_URL` apontando para a API, e
`APP_CORS_ORIGINS` no backend liberando essa origem.

## Hospedagem

O build é estático: qualquer host de arquivos serve, desde que faça **uma**
coisa a mais — devolver o `index.html` para caminho que não casa arquivo. Sem
essa regra, recarregar em `/pacientes` ou abrir o link direto de uma ficha
responde 404 do host. Medido, num host estático sem a regra:

```
GET /            -> 200
GET /pacientes   -> 404
```

A configuração já está no repositório:

| Arquivo | Host |
|---|---|
| `vercel.json` | Vercel — `rewrites` de `/(.*)` para `/index.html`, e cache imutável em `/assets` |
| `public/_redirects` | Netlify e Cloudflare Pages — copiado para `dist/` no build |
| — | Render Static Site: a regra equivalente é um *Rewrite* `/*` → `/index.html` no painel |

### Passo a passo

1. Criar o projeto no host apontando para o diretório `frontend/` do
   repositório. Build `npm run build`, saída `dist/`.
2. Definir `VITE_API_URL` com a origem da API (ex.: `https://psychology-api.onrender.com`).
   É lida no **build**: mudou a variável, refaça o deploy.
3. No backend, no painel do Render, `APP_CORS_ORIGINS` = a origem do frontend,
   exata e sem barra no fim. Nunca `*` — o próprio spec de CORS proíbe combinar
   `*` com credenciais.
4. Conferir na origem publicada, nesta ordem: entrar → **F5 em `/pacientes`**
   (prova a regra de rewrite do host) → cadastrar paciente → F5 em `/agenda`.

No passo 4, o F5 leva de volta ao login: é a dívida do refresh, não falha do
host. O que a recarga prova aqui é só que o host devolveu o `index.html` em vez
de um 404.

## Quando não sobe

| Sintoma | Causa provável |
|---|---|
| `npm install` falha com `Cannot find module 'node:path'` | Node antigo no PATH. Este projeto pede Node 20 ou mais novo (Vite 8) |
| A API não sobe, `PlaceholderResolutionException` | falta `JWT_SECRET` no ambiente |
| A API não sobe, porta em uso | outra aplicação na 8080. Suba com `PORT=8081` e rode o front com `API_URL=http://localhost:8081 npm run dev` |
| A tela de login diz "Nao foi possivel conectar ao servidor" | a API não está no ar. O front distingue isso de credencial inválida de propósito |
| `npm run e2e` falha logo no `/auth/registrar` | mesma coisa: o E2E **não** sobe a API, só o Vite |
| Recarregar a página desloga | esperado hoje — não há renovação de token. Ver [Sessão](#sessão) |
| Em produção, recarregar em `/pacientes` dá 404 do host | falta a regra `/*` → `/index.html`. Veja [Hospedagem](#hospedagem) |
| Em produção, toda chamada falha com erro de CORS | `APP_CORS_ORIGINS` não tem a origem exata do frontend (sem barra no fim) |

## O que a spec pede e ainda não existe

Lista curta e honesta do que o
[design](../docs/superpowers/specs/2026-08-26-frontend-design.md) descreve e o
código não faz:

- **Renovação de token** — `POST /auth/refresh` no boot, retry único de `401` e
  promise de refresh compartilhada. Nada disso existe; recarregar desloga.
- **Filtro e paginação na URL** (`useSearchParams`). Hoje é `useState`.
- **Telas de auditoria, lembretes, série recorrente e exclusão de paciente.** Os
  endpoints existem na API; o cliente não.
- **Tela de criar conta.** Só o `/entrar`.
- **`DialogoDeConsulta` fala HTTP direto**, furando a fronteira de camadas que
  vale nas outras três escritas.

## Documentação

- `../ai/Claude.md` — contexto do backend e as convenções da API
- `../docs/superpowers/specs/2026-08-26-frontend-design.md` — design do frontend, alternativas rejeitadas e as correções vindas da execução
- `../docs/superpowers/specs/2026-09-01-redesenho-produto-design.md` — o redesenho que trouxe a tela Hoje e a ficha de página única
- `../prototipo/` — o protótipo aprovado que `src/estilo/` reproduz
</content>
</invoke>
