# Frontend do consultório — design

**Data:** 2026-08-26
**Status:** implementado. Passos 1 a 6 da ordem de execução concluídos; resta hospedar.
**Escopo:** `psychology-web`. Depende de `psychology-api`, spec
`2026-08-26-sessao-com-refresh-design.md` — refresh, CORS e
`GET /pacientes/{id}/consultas` precisam existir antes das telas correspondentes.

## Objetivo

A API está no ar e não tem cliente. Este projeto é a interface do psicólogo:
entrar, ver a agenda do dia, cadastrar paciente, registrar a sessão, agendar a
terapia semanal e consultar a trilha de acesso.

Cobre as três fatias de uma vez — fundação, núcleo clínico e agenda — por decisão
explícita. A ressalva registrada: decisões da agenda serão tomadas antes de
qualquer aprendizado vindo das telas anteriores.

## Stack

| Camada | Escolha |
|---|---|
| Build | Vite |
| Linguagem | React + TypeScript |
| Rotas | React Router |
| Dados | TanStack Query |
| Estilo | Tailwind com tokens próprios, Radix pontual (Dialog, Tabs) |
| Testes | Vitest + Testing Library + MSW, mais um Playwright de fumaça |

Rejeitado — **hooks de fetch escritos à mão**: zero dependência de dados e
controle total, mas cache, deduplicação, revalidação e estado de erro viram
código repetido em cada tela, e a paginação do `PagedModel` seria reescrita três
vezes. A API tem invalidação cruzada de sobra — concluir lembrete, criar
anotação, cancelar série — que é exatamente onde o feito à mão erra.

Rejeitado — **RTK Query**: traz Redux junto, e não há estado global de cliente
que justifique uma store. A sessão é um contexto de três campos.

Rejeitado — **shadcn/ui**: aceleraria o CRUD, mas seu visual é característico e
teria que ser reestilizado inteiro para a direção do projeto — o trabalho volta
pela porta dos fundos, acompanhado de muito código que passamos a manter.

Rejeitado — **react-hook-form com Zod**: a API já valida e devolve `erros[]` com
campo e mensagem por campo. Duplicar o esquema no cliente cria duas fontes de
verdade que divergem em silêncio. O cliente valida só o óbvio — campo vazio —
para poupar uma ida ao servidor; o resto vem do envelope.

Rejeitado — **date-fns**: `Intl.DateTimeFormat` formata em pt-BR nativamente, e a
única aritmética de data do cliente é o preview semanal da série, que são somas
de sete dias. Quem gera as ocorrências de verdade é a API.

## Estrutura

```
src/
├── api/
│   ├── client.ts        fetch com base, Authorization, envelope de erro, retry de 401
│   ├── erro.ts          ErroApi
│   ├── tipos.ts         espelho dos DTOs
│   └── recursos/        auth · paciente · consulta · anotacao · lembrete · auditoria
├── features/
│   ├── auth/            LoginPage · SessaoProvider · useSessao
│   ├── pacientes/       lista · formulário · queries.ts
│   ├── ficha/           abas de anotações, lembretes e consultas · queries.ts
│   ├── agenda/          agenda do dia · formulário de consulta · série · queries.ts
│   └── auditoria/       trilha · queries.ts
├── ui/                  Botao · Campo · Chip · Dialogo · Paginacao · Tabela · EstadoVazio
├── estilo/              tokens · fontes
└── teste/               setup · handlers MSW · fixtures
```

Três camadas com fronteira dura: `api/recursos/*` só falam HTTP e devolvem tipos;
`features/*/queries.ts` embrulha cada função numa query ou mutation, com
`queryKey` nomeada e as invalidações declaradas ali; componentes consomem só
hooks. Nenhum componente monta URL ou toca em `Response`.

**Tipos escritos à mão** em `api/tipos.ts`. Rejeitado — **gerar de
`/v3/api-docs`**: manteria os dois repositórios alinhados sozinho, mas exige a
API no ar durante o build e amarra os projetos num passo de ferramenta. Com o
E2E de fumaça cobrindo o desalinhamento, não paga. Reconsiderar quando houver um
segundo consumidor da API.

## Sessão

O access token vive **apenas em memória**, num contexto React. Nunca
`localStorage`: quem aguenta o recarregamento é o cookie httpOnly, que o
JavaScript não alcança.

**No boot**, antes de renderizar qualquer rota, um `POST /auth/refresh`. O cookie
viaja sozinho: `200` restaura a sessão, `401` trata como visitante. É assim que
F5 não desloga sem que exista token persistido em lugar nenhum.

**Durante o uso**, o `client.ts` intercepta `401`, dispara o refresh e repete a
chamada original. Três regras que não são detalhe:

1. **Uma promise de refresh compartilhada.** Seis queries que tomam `401` ao
   mesmo tempo devem produzir **uma** rotação, não seis. Como a API revoga a
   cadeia inteira ao ver um refresh reapresentado, rotações concorrentes seriam
   lidas como roubo de token e derrubariam a sessão de um usuário legítimo. Este
   é o ponto de contato mais delicado entre os dois repositórios.
2. **Uma tentativa só.** `401` depois do refresh encerra a sessão e leva ao login.
3. **`/auth/*` não entra no retry**, para não fechar laço.

**Logout** chama `POST /auth/logout` e limpa o cache do TanStack Query — sem
isso, dado de paciente permanece em memória para o próximo login na mesma aba.

## Erro

Um caminho só, porque a API passou a responder todo erro no mesmo envelope:

```ts
if (!res.ok) throw new ErroApi(await res.json())  // { status, mensagem, erros[] }
```

Formulários leem `erros[].campo` e marcam o campo correspondente; o resto da
interface mostra `mensagem`. `404` de recurso de outro profissional chega como
"não encontrado", que é a intenção do backend — a interface não deve inventar
uma distinção que a API deliberadamente não faz.

## Desenvolvimento

O Vite proxia as chamadas para a API local, o que torna tudo same-origin e tira
CORS e `SameSite=None` do caminho em desenvolvimento.

O proxy **espelha os caminhos da API** (`/auth`, `/pacientes`, `/consultas`,
`/anotacoes`, `/lembretes`, `/auditoria`) em vez de prefixar com `/api`: o cookie
de refresh é marcado `Path=/auth`, e sob um prefixo o navegador veria
`/api/auth/refresh` e simplesmente não o enviaria. A renovação quebraria apenas em
desenvolvimento — o pior lugar para descobrir isso.

Em produção não há proxy: origem própria, CORS com credenciais, cookie cross-site.

## Rotas

```
/login                                    público — entrar e criar conta
/agenda                ?de= &ate=         sem parâmetros: hoje
/pacientes             ?nome=
/pacientes/:id         /anotacoes · /lembretes · /consultas
/auditoria             ?entidade= &entidadeId= &page=
```

**Filtro e paginação vivem na URL**, via `useSearchParams`, nunca em `useState`:
recarregar preserva, o link é compartilhável, e cada parâmetro casa um a um com o
da API — a tela não inventa vocabulário próprio. É também o que faz o botão
"voltar" do navegador se comportar.

Enquanto o refresh de boot não responde, tela de carregamento. Só depois as rotas
protegidas decidem entre conteúdo e redirecionamento.

## Telas, endpoints e invalidações

| Tela | Lê | Escreve | Invalida |
|---|---|---|---|
| Login / registro | — | `POST /auth/login`, `/auth/registrar` | — |
| Agenda | `GET /consultas?de&ate` | `POST`, `PUT`, `DELETE /consultas/{id}` | `consultas`, `consultasDoPaciente`, `auditoria` |
| Série | — | `POST /consultas/recorrentes`, `DELETE /consultas/series/{serieId}` | `consultas`, `auditoria` |
| Pacientes | `GET /pacientes?nome` | `POST`, `PUT`, `DELETE /pacientes/{id}` | `pacientes`, `paciente`, `auditoria` |
| Ficha · anotações | `GET /pacientes/{id}/anotacoes` | `POST`, `DELETE /anotacoes/{id}` | `anotacoes`, `auditoria` |
| Ficha · lembretes | `GET /pacientes/{id}/lembretes` | `POST`, `PATCH /lembretes/{id}/concluir`, `DELETE` | `lembretes`, `auditoria` |
| Ficha · consultas | `GET /pacientes/{id}/consultas` | — | — |
| Auditoria | `GET /auditoria?entidade&entidadeId&page` | — | — |

**Toda mutação invalida `auditoria`**, porque toda mutação grava um log no
servidor. Sem isso a trilha mostra um passado que já mudou — e a trilha é
justamente a tela em que estar desatualizada é pior.

**Concluir lembrete é otimista**, com rollback no erro: é um toggle barato e a
resposta visual imediata é o que faz a lista parecer viva. Nenhuma outra mutação
é otimista — criar anotação e agendar consulta podem falhar por regra de negócio
real (`409` de conflito), e fingir sucesso ali seria mentir sobre prontuário.

**A série tem preview no cliente:** as datas semanais que serão geradas aparecem
antes do envio, e o `409` de conflito é apresentado deixando claro que **nada**
foi criado. Um `409` silencioso numa série de doze sessões é a pior falha
possível dessa tela.

## Rascunho de anotação

**Só em memória.** Nada de `sessionStorage` ou `localStorage`: anotação clínica é
o dado mais sensível do produto e não fica em claro no navegador.

Isso obriga uma mitigação, sem a qual "em memória" vira "perde em silêncio":
`useBlocker` do React Router mais `beforeunload`, avisando antes de sair da ficha
com texto não enviado. Faz parte do escopo, não é polimento.

## Testes

`Vitest` com Testing Library e MSW. Os handlers do MSW respondem o contrato
**real** — envelope de erro, `PagedModel` com `page.totalElements`, `401` — para
que os testes exercitem o `client.ts` de verdade, e não um mock de módulo.

Merecem teste dedicado os casos que mordem:

- seis chamadas tomando `401` juntas produzem **uma** rotação;
- `401` no boot vira visitante, sem piscar conteúdo protegido;
- `erros[].campo` marca o campo certo do formulário;
- paginação lê `page.totalElements` e desabilita as pontas;
- filtro e página sobrevivem ao recarregamento (estão na URL);
- guarda de navegação dispara com rascunho pendente;
- `409` da série não deixa nenhuma ocorrência na interface.

**Um Playwright de fumaça** contra a API real em compose com Postgres:
`registrar → login → criar paciente → anotar → listar`. É o análogo do
`FluxoCompletoHttpTest`, atravessando os dois repositórios — o único teste capaz
de pegar contrato desalinhado, que é o risco estrutural de manter cliente e
servidor separados.

## Ordem de execução

1. **API** — CORS, migration `V8`, refresh com rotação, logout,
   `GET /pacientes/{id}/consultas`
2. **Web** — scaffold, tokens, `client.ts`, sessão, boot-refresh, login, rota
   protegida, layout; E2E de fumaça ligado desde aqui
3. **Web** — pacientes e ficha (anotações, lembretes, consultas)
4. **Web** — agenda, série, cancelamento
5. **Web** — auditoria
6. **Visual** — aplicar a direção do canvas sobre o esqueleto

O passo 2 existe para provar cedo o contrato entre os repositórios. A interface
fica crua por um tempo, de propósito.

## Direção visual

Editorial calmo: tipografia serifada de display (Newsreader) com sans de corpo
(IBM Plex Sans) e monoespaçada para identificadores e horários; paleta fria
dessaturada; densidade baixa e respiro generoso. Chips de status seguem o enum da
API — `AGENDADA`, `REALIZADA`, `CANCELADA`, `FALTOU`.

O canvas de referência com as seis telas está publicado como artefato e serve de
alvo, não de contrato: divergências de layout descobertas na implementação se
resolvem no código.

## Fora de escopo

Modo escuro. Responsivo de celular além de não quebrar — o alvo é desktop de
consultório. Internacionalização. Instalação como PWA e uso offline. Exportar
prontuário. Notificações. Nada disso é pré-requisito do MVP.

## Pontos em aberto

- **Hospedagem.** Vercel, Netlify ou estático no Render. Define só a variável de
  origem da API e o valor de `APP_CORS_ORIGINS` no backend.
- **Remote no GitHub.** O repositório é local até que você o crie.
- **Agenda semanal.** O desenho cobre o dia. A API já aceita intervalo, então a
  visão de semana é barata — mas não entra sem alguém pedir.

## Correções da spec vindas da execução

Escritas depois de rodar o esqueleto vertical. Onde a spec era ambígua ou
incompleta, o texto abaixo manda.

- **Fonte declarada é fonte carregada.** "Tokens da direção visual" não se
  cumpre com `--font-serif` apontando para uma família que o documento nunca
  baixa. O requisito só está atendido quando existe `<link>` (ou `@font-face`)
  e a família aparece na tela.
- **Autenticar é entrar na aplicação.** Nenhuma tela pode ficar num estado
  "autenticado, sem destino". Ao autenticar, o login navega para o destino que
  exigiu a sessão e, na falta dele, para a raiz.
- **A rota protegida carrega o destino.** O redirecionamento para `/login`
  leva o caminho pretendido no `state`, para o login devolver o usuário onde ele
  estava tentando chegar.
- **Envio é estado, não evento.** Todo formulário trava o botão de envio
  enquanto a chamada está em voo. Vale sobretudo para anotação, que a API não
  deixa editar nem apagar: uma gravação duplicada fica no histórico para sempre.
- **Formulário é `<form>`.** `Enter` no último campo envia.
- **A mensagem de erro do campo fica fora do `<label>`.** Dentro dele, ela
  entra no nome acessível do input. Ligação por `aria-describedby`, mais
  `aria-invalid` no input.
- **Marca de campo obrigatório olha o valor atual**, não o retrato do último
  envio: ela sai assim que o campo é preenchido.
- **`e2e/` fica fora do glob do Vitest.** O `test()` do Playwright não roda
  dentro do Vitest.
- **Placeholder tem dono e data de morte.** Um placeholder criado porque a
  peça de verdade ainda não existe só entra acompanhado do item, na tarefa que
  cria a peça, que o remove.
