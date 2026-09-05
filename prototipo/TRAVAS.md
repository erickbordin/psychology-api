# Travas — Fase 0

Uma linha por trava encontrada ao percorrer um cenário.
`[escopo]` marca o que não se conserta no protótipo e sobe para revisão humana.
`[decisão do dono]` marca uma regra de produto que apareceu no protótipo sem
nunca ter sido decidida — nem pela spec, nem pelo dono do produto — e que só
ele pode resolver. Não é layout, não é integração entre telas: é escolha de
negócio em aberto.

| Cenário | Onde | O que travou | Resolução |
|---|---|---|---|
| C1 | Tela Hoje — botão "Resolver" em cada linha de `#pendencias` | Botão renderizado sem handler de clique; clicar não fazia nada, então não dava pra fechar uma pendência de ontem em 1 clique como o cenário exige | `[layout]` Adicionado handler delegado no container `#pendencias`. Fix round 1: em vez de apagar a linha (sumir sem confirmação esconde se resolveu ou quebrou), o clique marca a linha com `.resolvida` (texto riscado, cor `--tinta-3`) e o botão vira `<span class="chip">Resolvido</span>` — front-end apenas, sem persistência |
| C1 | Tela Hoje — título "Pendente de ontem" acima de `#pendencias` | O rótulo prometia um recorte temporal (só "de ontem") que o conteúdo não tinha: das 4 pendências, 3 são de ontem (Rui e Clara sem status, Otávio sem anotação), mas "Bruna Teixeira · 4 sessões" sem baixa é acúmulo de semanas, não de ontem | `[layout]` Trocado o título para "Ficou pendente", sem recorte temporal, cobrindo os dois tipos de pendência que a seção de fato lista |
| C2 | Tela Agenda — grade semanal (`#grade`) | Clicar num `.slot` livre não fazia nada (nem existia handler) e clicar num `.slot.ocupado` também não avisava nada — os dois cliques mais importantes do cenário ("agendar o retorno para a próxima terça no mesmo horário") eram engolidos em silêncio, e não dava pra saber se a próxima terça já tinha horário ocupado antes de tentar marcar | `[layout]` Grade agora recalcula dias/ocupação por semana real (`semanaOffset`, ± 7 dias a partir da segunda de "hoje"). Slot livre marca `.provisorio` ao clicar (visível, alternável) e atualiza `#status-agenda`; slot ocupado nunca agenda — mostra em `#status-agenda` quem já está no horário. "Semana seguinte/anterior" recalculam a grade inteira (dias + ocupação) e o rótulo do subtítulo, não só o texto. Percurso "próxima terça, mesmo horário" a partir da tela Agenda: 2 cliques (Semana seguinte → clicar o slot livre), dentro do limite de 3 |
| C2 | Tela Agenda — slot provisório não sabe de qual paciente é o retorno | A Agenda sozinha não tem como saber que o retorno marcado é da Helena Vaz — isso exigiria a Ficha (ainda não construída) passar o paciente de contexto para a Agenda, uma decisão de integração entre telas que não estava no brief desta tarefa | `[fatia]` Agenda precisa de seletor de paciente ao agendar por slot; pela ficha o paciente é implícito (spec §4 ② e ③). Não é conflito de spec — é trabalho da Fatia 1/2. **RESOLVIDA pela Task 5 (Fase 0.5)**: os três pontos de entrada de consulta (+ Nova consulta, slot livre da grade, Agendar retorno) abrem o mesmo `#dialogo-consulta`, que pergunta o paciente (`#con-paciente`) — travado quando implícito no retorno, escolhível nos outros dois casos |
| C2 | `dados.js` — `DADOS.linhaTempo` (rascunho do brief da Task 5) | O rascunho trazia o status trocado: a sessão mais recente antes de hoje (26 ago) vinha `realizada`, e uma sessão anterior (19 ago) vinha `faltou`. Isso contradiz `DADOS.hoje.sessoes` (`faltouAntes: true` para Helena) e o próprio C2/`CENARIOS.md` — "faltou sem avisar **na semana passada**" é a sessão mais recente, não uma de duas semanas atrás. Sem o ajuste, às 8h55 a tela Hoje e a Ficha contariam versões diferentes do mesmo fato | `[layout]` Trocado o status/pago/anotação entre as entradas de 26 ago e 19 ago — mesmos valores de campo do brief (180, `true`/`false`, os dois textos de anotação), só reordenados — deixando a falta sem aviso na entrada mais recente, coerente com a tela Hoje |
| C2 | Cabeçalho da Ficha (`.cabecalho-ficha .sub`) | O markup do brief só trazia "34 anos · desde março de 2026 · R$ 180,00 por sessão" — sem contagem de sessões. "Desde março" sozinho não responde "há quanto tempo ela vem" em número de encontros, e o cenário pede essa resposta sem caçar | `[layout]` Acrescentado "14 sessões" ao subtítulo (fato já estabelecido em `CENARIOS.md`), entre "desde março de 2026" e o valor da sessão |
| C2 | Ficha — "Registrar sessão", "Agendar retorno", "Editar"; tela Pacientes — busca e "+ Novo paciente"; aside da Ficha — checkboxes de `#pendencias-paciente` | O markup do brief (Steps 3 e 4) só definia a estrutura, sem nenhum handler de clique/input — mesmo defeito da trava de C1 no botão "Resolver": todo clique seria engolido em silêncio, quebrando os dois cliques centrais do cenário às 9h52 | `[layout]` Handlers adicionados para todos. "Registrar sessão" grava uma linha nova (`realizada` + anotação) no topo da linha do tempo, sem sair da ficha — a mesma ação que documenta e marca presença, prova da fusão. "Agendar retorno" confirma inline a próxima terça no mesmo horário (09:00), calculada a partir de `DADOS.hoje`/`MESES_AGENDA`. "Editar" e "+ Novo paciente" avisam "fora do escopo deste protótipo" (padrão já usado na Agenda). A busca filtra `DADOS.pacientes` por nome de verdade. Os checkboxes de pendência marcam `.resolvida` visivelmente (convenção do fix round 1 em C1) |
| C2 | Ficha — botão "Agendar retorno" (`#agendar-retorno`) | A confirmação do retorno fica só na Ficha (mensagem inline); não escreve na grade real da Agenda (`provisorios` de `#grade`). Sem sair da ficha o cenário está resolvido, mas quem for conferir a Agenda depois não vai ver terça 8/set 09:00 marcada lá | `[fatia]` Integração de estado entre Ficha e Agenda é trabalho da Fatia 1/2. Spec §4 ② e ③ preveem os dois fluxos; não há conflito de desenho a resolver. |
| C2 | Tela Pacientes — qualquer linha de `#lista-pacientes` | A ficha do protótipo é hardcoded para um paciente; qualquer linha da lista (Helena Vaz, Rui Sampaio ou Clara Bueno) abre a mesma Ficha, sempre com os dados da Helena | `[fatia]` Roteamento por id de paciente (`/pacientes/:id`) é trabalho da Fatia 2. No protótipo, uma ficha só basta para percorrer o C2. |
| C3 | `dados.js` — `DADOS.financeiro.devendo` vs. ficha da Helena Vaz (`linhaTempo`, 26 ago) | A ficha da Helena mostra uma sessão com `pago: false` (26 ago, faltou sem avisar), mas `devendo` listava só Bruna Teixeira e Ivan Prado — quem fecha o mês pela tela Financeiro não veria que a Helena também deve, e quem abre a ficha dela veria uma sessão a receber que a tela financeira nega. Os totais também só fechavam (`recebido + aReceber = faturado`) sem contar essa sessão | `[layout]` Adicionada Helena Vaz a `devendo` (1 sessão, R$ 180). `aReceber` subiu de 1260 para 1440 (bate com a soma de `devendo`); `faturado` subiu de 4320 para 4500 (a sessão da Helena não estava contada em lugar nenhum); `recebido` ficou em 3060 — ninguém recebeu esse dinheiro. Confere: 3060 + 1440 = 4500 |
| C3 | `dados.js` — `DADOS.financeiro.mes` | O rascunho do brief trazia `mes: 'setembro'` e o subtítulo "setembro de 2026", mas todas as datas da tela (as 3 pessoas devendo, as 4 sessões da semana passada, a falta da Helena) são de agosto — "hoje" é 1º de setembro (`DADOS.hoje`), então o fechamento do C3 ("é dia 30") é do mês que passou, agosto, não do mês corrente | `[layout]` Trocado `mes` para `'agosto'` e o subtítulo da tela para "agosto de 2026" |
| C3 | `#devendo` — texto "N sessões" | O rascunho do brief tinha `${d.sessoes} sessões` fixo no plural; com a Helena entrando com 1 sessão, o texto sairia "1 sessões" (erro de concordância) | `[layout]` Pluralização condicional: "1 sessão" / "N sessões" |
| C3 | `#baixa-lote` — botão "Dar baixa" e os 4 checkboxes | O markup do brief (Step 1) não tinha handler nenhum: os checkboxes eram estáticos (só `checked`, sem id/valor) e o botão "Dar baixa" não fazia nada — o resumo "4 selecionadas · R$ 720" ficaria escrito na pedra mesmo desmarcando caixas, e a ação central do cenário (dar baixa sem abrir 4 telas) seria um clique engolido em silêncio | `[layout]` Checkboxes ganharam `data-valor`/`data-paciente`; um handler de `change` recalcula "N selecionadas · R$ X" a cada marcação/desmarcação. "Dar baixa" marca as linhas selecionadas como `.baixado` (risca o texto, desabilita a caixa, troca a ação por `<span class="chip pago">Baixa dada</span>`), abate a sessão do registro correspondente em `devendo` (e remove o paciente da lista se a dívida zerar), e soma o valor em `recebido`/subtrai de `aReceber` — os indicadores do topo e o cartão "Quem está devendo" são repintados na hora. Custo: 1 clique em "Dar baixa" (as 4 sessões já vêm marcadas por padrão) — ou 2 cliques contando a navegação até a tela Financeiro — bem abaixo do limite de 5 |
| C4 | Tela Ajustes — checkbox "Incluir meu título profissional", select de antecedência e link "Abrir trilha" | O markup do brief (Step 1) não trazia nenhum JS: marcar/desmarcar o checkbox e trocar o select não mudavam nada na prévia, e "Abrir trilha" (`href="#/ajustes"`) só recarregava a própria tela — os três cliques mais importantes da tela (a demonstração viva da regra de privacidade, e o aviso de "fora do protótipo") seriam engolidos em silêncio | `[layout]` Adicionado `pintarPreviaEmail()`, chamado no load e nos eventos `change` do checkbox e do select: desligado, o remetente é só `Marina Aguiar`; ligado, vira `Marina Aguiar, psicóloga` — o assunto nunca muda, porque a neutralidade do assunto não é opção do psicólogo. O texto de antecedência na prévia (`#previa-antecedencia`) também troca com o select. "Abrir trilha" ganhou handler com `preventDefault()` e aviso em `#status-ajustes`: "fora do escopo deste protótipo" |
| C4 | Cartão "Como o aviso chega" — data do exemplo na prévia | O rascunho do brief usava "terça, 2 de setembro" — mas no calendário interno do protótipo (`segundaAgenda`, `tela-agenda`) 31 de agosto de 2026 é segunda-feira, então 2 de setembro é quarta, não terça. Um exemplo de data que contradiz o próprio calendário do protótipo mina a credibilidade da prévia bem no artefato mais sensível da tarefa | `[layout]` Trocada a data do exemplo para "terça, 8 de setembro" (a terça seguinte, mesmo horário), consistente com o calendário usado na Agenda |
| C4 | Tela Ajustes — cartão "Aviso de consulta" vs. Ficha do paciente (consentimento) | O brief isola antecedência e título profissional em Ajustes sem dizer onde vive o consentimento da paciente. Percorrendo o C4 só por Ajustes, dá pra achar que ligar essas opções já basta para o e-mail sair — escondendo que o envio depende também do consentimento, que é por paciente e mora noutra tela (a Ficha, Task 5) | `[layout]` Acrescentada a frase "Vale para quem autorizou o aviso — o consentimento fica registrado na ficha de cada paciente, não aqui" no cartão "Aviso de consulta", deixando explícita a costura entre as duas telas sem precisar de link (não há, no protótipo, uma ficha por paciente para apontar) |
| C4 | Ficha do paciente (`tela-ficha`, Task 5) e tela Pacientes (`+ Novo paciente`) | Percorrendo o C4 de ponta a ponta ("cadastrei uma paciente nova hoje... preciso registrar esse consentimento") não existe nenhum controle para de fato fazer isso: "+ Novo paciente" já avisa "fora do escopo deste protótipo" (decisão da Task 5), e o único indicador de consentimento que existe — o chip `Aviso por e-mail: não autorizado` no cabeçalho da ficha — é texto estático hardcoded para a Helena Vaz (não lê `DADOS.pacientes[].consentimento`, não responde a clique). Não há ficha nenhuma para representar a "paciente nova" do cenário | `[fatia]` Registrar/alternar consentimento é trabalho do cadastro/edição de paciente (Fatia 1 ou 2), já descontinuado no protótipo pela própria Task 5. `tela-ficha` e `tela-pacientes` estão fechadas nesta tarefa — não é conserto de layout possível aqui. Não contradiz a spec (§5.4 e §4③ preveem o campo `consentimentoAvisoEm` e "estado do consentimento" no cabeçalho da ficha; só falta a interação, que é trabalho de implementação real, não de protótipo) |
| C1 → C2/C3/C4 (só aparece no percurso do dia inteiro, seguido) | Botão "👁 Modo privacidade" (`#privacidade`, tela Hoje) — efeito global via `body.privado` | O clique liga uma classe no `<body>`, e a regra que borra (`estilo.css`, `body.privado .paciente, .nome-grande, .linha-pendencia span:first-child { filter: blur(5px) }`) reaproveita nomes de classe genéricos que também existem nas telas Pacientes (`.paciente` na lista), Ficha (`.linha-pendencia` em `#pendencias-paciente`) e Financeiro (`.linha-pendencia` em `#devendo` e `#baixa-lote`) — telas que continuam no DOM, só com `hidden`, e não recarregam ao trocar de rota. Ligar o modo na tela Hoje e navegar pela barra lateral para Pacientes borra os nomes da lista; para a Ficha, borra as pendências do paciente; para o Financeiro, borra "Quem está devendo" e a lista de baixa em lote — sem que o psicólogo tenha pedido isso em nenhuma dessas telas. O botão não ganha nenhum estado visual (`.ativo` ou equivalente) quando ligado, e não existe réplica dele em nenhuma outra tela: quem sai da Hoje com o modo ligado não tem como saber que está ligado, nem como desligar, sem voltar até lá | `[layout]` Achada neste percurso sequencial da Task 8 e registrada sem correção naquele momento — o mandato da Task 8 era percorrer e documentar, não consertar o protótipo. Corrigida depois, na onda de correção da revisão final (item E): o blur passou a ser escopado a `#tela-hoje` (em vez de `body.privado` global) e o botão `#privacidade` ganhou estado visível (`.ativo`) quando ligado. É o tipo de falha que só aparece percorrendo várias telas na mesma sessão sem recarregar — nenhum percurso isolado de tela única (Tasks 3, 5 e 6) passava por essa combinação |
| C1 ↔ C2 | Tela Hoje — `#lista-dia` (linha de Helena Vaz, 09:00) vs. Ficha — botão `#registrar-sessao` | Cumprir o C2 ("registrar a anotação desta sessão, marcar que ela aconteceu") pela Ficha grava só em `DADOS.linhaTempo`; `DADOS.hoje.sessoes` — a fonte da lista da tela Hoje — não muda. Voltando para Hoje pelo link da navegação (sem editar hash), a linha das 9h de Helena continua com os dois botões "Realizada"/"Faltou" disponíveis, e o cartão de destaque continua anunciando-a como "Próxima, em 20 minutos", como se a sessão ainda não tivesse acontecido. Quem faz o dia inteiro pela interface vê o mesmo evento com dois estados diferentes em duas telas | `[fatia]` Mesma natureza da trava já registrada em C2 ("Agendar retorno" não escreve na grade da Agenda, linha acima): exige um modelo de consulta único e compartilhado entre telas (spec §5.3, `Consulta` com status; §5.6 `GET /painel/hoje` agregando da mesma fonte em que a Ficha grava), que é trabalho de back-end real, não de protótipo estático com dois objetos JS independentes. Não contradiz a spec — ela já prevê a fonte única; falta é a integração, que é da Fatia 1/2 |
| C2 → C3 | Ficha — botão `#registrar-sessao` vs. Financeiro — `#ind-faturado`/`#ind-a-receber`/`#devendo` | "Registrar sessão" grava a sessão de hoje da Helena como `realizada`/`pago: false` (R$ 180) só em `DADOS.linhaTempo`. `DADOS.financeiro` (faturado/recebido/a receber e a lista "Quem está devendo") não é tocado. Fazendo o C2 e em seguida indo ao Financeiro pela navegação (C3, "quero saber quanto faturei... e quem está devendo"), os indicadores e a lista de devedores ficam exatamente como antes — sem a sessão nova, sem o valor novo a receber —, mesmo a sessão já estando lançada e não paga na Ficha. Isolado, cada tela fecha as próprias contas (já verificado nas Tasks 5 e 6); só o percurso do dia inteiro, sessão seguida de fechamento do mês, expõe que as duas fontes de dinheiro não conversam | `[fatia]` Mesma causa da trava anterior, e da já registrada em C3 (Helena reconciliada manualmente em `dados.js` contra a ficha, na Task 6): sem um `Consulta.valor`/`pagoEm` único que tanto a Ficha quanto o Financeiro leem (spec §5.3, §5.6), qualquer front-end — protótipo ou produto — precisaria replicar manualmente cada lançamento em dois lugares. Não contradiz a spec, que já desenha a fonte única; é trabalho de agregação real da Fatia 3 |
| Revisão final | Tela Agenda — grade semanal (`#grade`) | **§4 ②** "remarcar arrastando" não existe, e nada na tela menciona remarcação. É interação de UI pura — a única classe de coisa que um protótipo navegável pode validar — e não foi tentada | `[fatia]` Não prototipado nesta fase; fica para a Fatia 1/2 desenhar e implementar o arrastar-para-remarcar |
| Revisão final | Tela Ajustes — cartão "Aviso de consulta" | **§4 ⑤** "texto do aviso" não existe — só a antecedência é editável. Tem consequência de privacidade: se o psicólogo puder editar o texto, a garantia de "zero conteúdo clínico" de §7.1 deixa de ser estrutural e passa a depender de confiança | `[fatia]` Não prototipado; ao desenhar o campo de texto do aviso na Fatia 1/2, decidir como manter a garantia de §7.1 estrutural (template fixo e/ou validação de conteúdo), não dependente de confiança. **RESOLVIDA (parcialmente) pela Task 6 — Fase 0.5**: `#aviso-texto` prototipado no cartão "Aviso de consulta", editável com marcadores `{paciente}`/`{data}`/`{hora}`, atualizando a prévia ao vivo. O assunto e a linha de descadastro continuam fixos, fora do textarea — a garantia estrutural de §7.1 permanece intacta para o assunto (a parte que aparece na tela de bloqueio sem abrir nada). Para o **corpo**, a garantia deixou de ser estrutural e virou confiança, como esta própria linha já previa: a tela avisa "não escreva aqui nada sobre o motivo da consulta", mas nada valida o conteúdo digitado. Decidir template fixo/validação de conteúdo para o corpo continua em aberto para a Fatia 1/2 |
| Revisão final | Cabeçalho da ficha (`.cabecalho-ficha`) | **§4 ③** contato do paciente ausente do cabeçalho da ficha. O cabeçalho ganhou "14 sessões" (que a spec não pede) e perdeu o contato (que a spec pede). Pesa porque §5.4 torna o e-mail obrigatório quando o canal é EMAIL, e o C4 é justamente ligar o aviso por e-mail — a ficha nunca mostra endereço nenhum | `[fatia]` Cabeçalho da ficha precisa incluir o contato do paciente na Fatia 1/2, conforme §4③/§5.4 |
| Revisão final | Linha do tempo da ficha (`#linha-tempo`) | **§4 ③** o campo `valor` existe em cada item de `DADOS.linhaTempo` e nunca é pintado na linha do tempo. É o campo que §5.3 desenha como snapshot ("reajustar de R$150 para R$200 não reescreve o histórico") — sem exibi-lo, o protótipo não mostra o caso que motivou a decisão | `[fatia]` Pintar o valor de cada sessão na linha do tempo na Fatia 1/2, para demonstrar o snapshot de §5.3 |
| Revisão final | Tela Hoje — faixa de pendências (`#pendencias`) | **§4 ①** a faixa de pendências não diz o que está pendente. As três pendências (sessão sem status, realizada sem anotação, realizada sem baixa) são coisas diferentes que no produto real exigem três ações diferentes. O protótipo dá um botão "Resolver" genérico para as três, o `data-tipo` que as distinguiria é emitido no markup e nunca é lido, e o texto da linha não conta o que falta | `[fatia]` Diferenciar a ação por `data-tipo` (marcar status, abrir anotação, dar baixa) e o texto da linha dizer o que falta, na Fatia 1/2 |
| Revisão final | Todas as telas | Nenhuma tela tem estado vazio. Todas assumem consultório em regime — com pacientes, sessões e histórico. O dia 1 do psicólogo pagante, zero paciente/zero sessão/zero dinheiro, não foi desenhado em tela nenhuma | `[fatia]` Desenhar e prototipar o estado vazio de cada tela (Hoje, Agenda, Pacientes, Ficha, Financeiro) na Fatia 1/2. **RESOLVIDA pela Task 6 — Fase 0.5**: estados vazios desenhados e prototipados para Hoje (`#lista-dia` mantendo régua/trilho/linha do agora, `#pendencias`), Pacientes (`#lista-pacientes`, distinto do "busca não achou nada"), Financeiro (`#devendo`, e `#baixa-lote`/seu `<h2>` escondidos por inteiro) e a linha do tempo da Ficha (`#linha-tempo`) — Agenda não precisou de estado próprio, a grade já nasce livre e honesta quando `DADOS.hoje.sessoes` está vazio. Alternador "🛠 Ver como fica vazio" no cabeçalho da tela Hoje troca `DADOS` por `DADOS.vazio` e repinta todas as telas; é andaime de protótipo (marcado como tal), não feature do produto |
| Revisão final | As cinco superfícies de entrada de dado deixadas como stub "fora do escopo" (Editar/Novo paciente, Nova consulta, Série semanal, Abrir trilha) | **§6.2** a Fase 0 não prototipou formulário nenhum. Quando o erro aparece, obrigatório visível antes do erro, foco no primeiro campo inválido, tipo certo de campo — nada disso saiu desta fase com uma linha de desenho, e "sem validação em formulários simples" foi a queixa que originou o redesenho | `[fatia]` Prototipar ao menos um formulário representativo (ex.: novo paciente) aplicando §6.2 na Fatia 1/2, antes de generalizar para os demais. **Atualização — Fase 0.5**: duas das cinco deixaram de ser stub. **Editar/Novo paciente RESOLVIDA pela Task 4** (`#dialogo-paciente`, com §6.2 aplicado: erro no blur/submit, foco no primeiro inválido, obrigatório/opcional visível antes do erro, validação condicional de e-mail). **Nova consulta RESOLVIDA pela Task 5** (`#dialogo-consulta`, cobrindo os três pontos de entrada — "+ Nova consulta" da Agenda, slot livre da grade, e "Agendar retorno" da Ficha — com conflito de horário checado antes do envio e data no passado avisando sem bloquear, §6.3). Sobram stub de verdade: **Série semanal** (ver linha própria abaixo, Fase 0.5) e **Abrir trilha** (continua "fora do escopo deste protótipo"; trilha real é spec §4⑤/§7.4, ainda não prototipada) |
| Revisão final | `dados.js` — `DADOS.linhaTempo` (sessão em que a Helena faltou) e `DADOS.financeiro.devendo` | "Falta é cobrada" virou regra de produto sem ninguém decidir: a sessão em que a Helena faltou é `faltou` + `pago: false`, entra em `financeiro.devendo`, com `faturado` subindo para acomodá-la — o protótipo afirma, em duas telas, que sessão em que o paciente falta é faturada e vira dívida. A spec não decide isso em lugar nenhum; a regra entrou por reconciliação de dado (Task 6), não por decisão de produto | `[decisão do dono]` **RESOLVIDA — decisão do dono, 4 de setembro de 2026**: falta não é cobrada. Spec §3/§5.3 atualizada registrando a decisão (o porquê ali é do documento, não do dono). Corrigido pela Task 7: `dados.js` (`financeiro.faturado`/`aReceber`/`devendo` sem a Helena) e o chip da ficha (`pintarLinhaTempo()`, sessão `faltou` mostra "não cobrada", não "a receber") |
| Redirecionamento visual | `tokens.css`, `estilo.css`, `index.html` — toda a interface | A regra "azul e SO acao: botao primario, item ativo da navegação, ícone de seção" nunca tinha sido decidida pelo dono do produto, só um padrão que a Fase 0 adotou sozinha ao inventar os tokens. O dono viu o protótipo publicado e quis redirecionar para a linguagem do Google Calendar — onde azul é a cor do **evento**, não do botão; manter a regra antiga e adotar o Calendar era impossível | `[decisão do dono]` Regra substituída, com aval explícito do dono: **"ação é forma, não cor"** — o botão primário vira pílula elevada com sombra (o "Criar" do Calendar), evento vira bloco chapado, sem sombra, raio pequeno. Paleta, tipografia (Roboto) e densidade trocadas para os valores do Calendar; tela Hoje reestruturada como faixa horizontal (sessões posicionadas pelo horário, linha do "agora" usando `DADOS.hoje.agora`); grade da Agenda troca `gap`+fundo simulado por `border` de verdade e ganha cabeçalho "SEG 31" com o dia de hoje em círculo preenchido. Nenhum handler, listener, mensagem de status ou fluxo mudou |
| C2 | Ficha → "Agendar retorno" | Confirma um texto na tela, mas nunca pergunta **quando**. Nao ha campo de data, nem escolha de horario — o retorno nao tem data de retorno. | `[decisao do dono]` Levantado pelo dono ao ver o prototipo aprovado; adiado por ele ("isso iremos vendo depois"). Precisa definir se a data vem sugerida (mesma hora, +7 dias) com opcao de trocar, ou se e escolha livre na grade da Agenda. Toca spec §4 ② e ③. **RESOLVIDA pela Task 5**: "Agendar retorno" abre `#dialogo-consulta` com paciente implícito (travado) e data sugerida em +7 dias, mesmo horário — e editável, porque quem está na frente do psicólogo pode combinar outro dia. O dono decidiu isso implicitamente ao aprovar a entrega da Task 5; esta nota **não** escolhe entre "sugerida" e "livre na grade" em nome dele — registra que a Task 5 entregou a primeira opção (sugerida + editável) e ele a aceitou |

## Percurso final (Task 8) — C1 → C2 → C3 → C4 em sequência

Servido com `python3 -m http.server` em segundo plano, HTTP 200 confirmado em
`index.html`/`tokens.css`/`estilo.css`/`dados.js`, servidor derrubado antes de
fechar esta tarefa. Sem navegador disponível, o percurso foi feito lendo o
markup e o JavaScript que cada tela produz, seguindo estritamente os elementos
de interface (barra lateral, links da própria tela, botões) — nunca editando
o hash na mão.

**Navegação, tela a tela, sem edição de hash:** `#/hoje` (carga inicial) →
clique em "Abrir ficha" no cartão de destaque → `#/ficha` (Helena Vaz, C2) →
clique em "Financeiro" na barra lateral → `#/financeiro` (C3) → clique em
"Pacientes" na barra lateral → `#/pacientes` → clique em "+ Novo paciente"
(trava conhecida, item 4 do ruído — sem cadastro real, sem consentimento a
registrar) → clique em "Ajustes" no rodapé da barra lateral → `#/ajustes`
(C4, parcial). **Nenhuma trava de navegação por edição manual de URL foi
encontrada** — as cinco telas e a ficha são alcançáveis só com cliques, em
qualquer ordem que os quatro cenários pedem.

O que a sequência revelou não foi problema de rota, foi problema de estado:
três costuras novas entre telas (linhas acima desta seção), inexistentes nos
percursos isolados tela a tela porque cada um partia de `DADOS` "limpo" —
nunca acontecia registrar algo numa tela e depois ir conferir outra na mesma
sessão:

1. **Modo privacidade vazava da Hoje para Pacientes, Ficha e Financeiro** —
   achado neste percurso sequencial da Task 8 e registrado `[layout]` sem
   correção naquele momento, porque o mandato da Task 8 era percorrer e
   documentar, não consertar o protótipo. Corrigido depois, na onda de
   correção da revisão final: o blur passou a ser escopado a `#tela-hoje`, e
   o botão `#privacidade` ganhou estado visível quando ligado.
2. **Hoje e Ficha discordam sobre se a sessão das 9h já aconteceu** —
   `[fatia]`.
3. **Ficha e Financeiro discordam sobre quanto se está devendo** —
   `[fatia]`.

**C4 saiu parcialmente percorrido.** O cenário foi escrito para percorrer o
registro do consentimento de uma paciente cadastrada hoje, e o protótipo não
tem onde fazer isso: o mapa de telas da Fase 0 nunca previu um formulário de
edição/cadastro de paciente (só a ficha de leitura da Helena Vaz e o aviso
"fora do escopo" em "+ Novo paciente" e "Editar"). O passo do consentimento
**não foi validado** — não porque travou, mas porque não existe superfície
nenhuma para tentar. Os dois passos seguintes do cenário, sim, foram
percorridos com sucesso pela superfície que existe (tela Ajustes, ligada à
ficha da Helena como demonstração): escolher "24 horas antes" no seletor de
antecedência atualiza a prévia na hora, e o cartão "Como o aviso chega" mostra
o assunto sempre neutro ("Lembrete de agendamento") independente do
remetente — exatamente a garantia de privacidade que o cenário testa ("o
marido dela usa o mesmo computador"). Isso já estava coberto pelas travas de
C4 registradas nas tarefas anteriores; nada novo foi encontrado nessa parte.

Antes da Fatia 2, a spec §4③ precisa ganhar a especificação do formulário de
edição/cadastro de paciente — hoje ela descreve o cabeçalho, a linha do tempo
e as pendências da ficha, mas não o formulário que grava
`consentimentoAvisoEm`/`canalAviso` (§5.4). Sem isso especificado, a Fatia 2
não tem onde pousar a interação que o C4 pede.

## Classificação final

Releitura completa das 28 linhas da tabela (as 20 desta tarefa mais as 8
acrescentadas na revisão final):

- `[layout]` — 14. As 14 agora correspondem de fato à definição do balde
  ("já corrigido no protótipo"): a única que não correspondia — a trava do
  vazamento do modo privacidade (linha "C1 → C2/C3/C4"), marcada `[layout]`
  mas com a Resolução dizendo "não corrigido nesta tarefa" — foi corrigida
  nesta rodada de correção final (item E: blur escopado a `#tela-hoje`,
  estado visível dado ao botão `#privacidade`). A exceção que existia na
  contagem anterior deixou de existir; nenhuma linha `[layout]` fica hoje
  sem correção de fato aplicada.
- `[fatia]` — 13 (6 já existentes desta tarefa + 7 novas da revisão final:
  remarcar arrastando §4②, texto do aviso §4⑤, contato ausente do cabeçalho
  §4③, valor nunca pintado na linha do tempo §4③, faixa de pendências sem
  diferenciar tipo de ação §4①, ausência de estado vazio em toda tela,
  ausência de qualquer formulário prototipado §6.2).
- `[decisão do dono]` — 2 ("falta é cobrada" virou regra de produto sem
  ninguém decidir; mais uma, posterior a esta releitura: o redirecionamento
  da direção visual para a linguagem do Google Calendar, com a substituição
  de "azul é só ação" por "ação é forma, não cor").
- `[escopo]` — 0.

14 + 13 + 2 + 0 = 29 — bate com o total de linhas da tabela após o
acréscimo da linha de redirecionamento visual (as 28 desta releitura mais 1).

Nenhuma trava, nova ou antiga, contradiz a spec
(`docs/superpowers/specs/2026-09-01-redesenho-produto-design.md`). As treze
`[fatia]` não são todas a mesma causa: as 6 já existentes desta tarefa
compartilham uma única causa raiz — o protótipo é HTML estático com objetos
JS locais e sem persistência, enquanto a spec já desenha a fonte única de
dados (§5.3, §5.6) que vai resolvê-las na Fatia 1-3. As 7 novas da revisão
final são de outra natureza: desenho que a spec já prevê e a Fase 0 não
chegou a prototipar (§4①③⑤, §6.2) ou nem tentou (arrastar para remarcar,
estado vazio). Em nenhum dos treze casos é decisão de desenho a rediscutir —
a única linha que é decisão de produto em aberto está isolada, sozinha, no
balde `[decisão do dono]`.

## Limitações

Isto foi percorrido contra cenário escrito, não usado por psicólogo nenhum —
"validação contra hipótese, não pesquisa com usuário" (spec §9, Fase 0): o
protótipo aprovado aqui não é um produto validado, só um mapa de telas que
não contradiz o que foi pensado. E o C4, como registrado acima, saiu desta
fase **parcialmente** percorrido: o passo de registrar consentimento nunca
teve onde acontecer, porque a Fase 0 não previu formulário de edição de
paciente. Isso não bloqueia o veredito abaixo — a ausência do formulário já
está classificada `[fatia]` desde a Task 5, não é contradição de spec — mas é
uma lacuna real que a spec precisa fechar (§4③) antes de a Fatia 2 começar.

**Veredito.** Nenhuma trava `[escopo]` foi encontrada — zero contradição com a
spec, e isso se manteve depois de duas releituras independentes desta tabela.
Isso mede a coisa certa (o protótipo não afirma nada que a spec desminta), mas
não é o mesmo que "desenho completo": as sete linhas `[fatia]` acrescentadas
na revisão final, acima, mostram seis células de §4 que a fase deveria ter
fechado e não fechou.

- **Fatia 1 liberada** (Login, Hoje, Agenda) — nenhuma trava em aberto pesa
  sobre essas três telas.
- **Fatia 2 bloqueada** até §4③ e §4⑤ ganharem, respectivamente, o formulário
  de edição/cadastro de paciente — onde o consentimento do aviso é registrado,
  a mesma lacuna que já deixava o C4 parcialmente percorrido (ver Limitações
  acima; isto é companhia daquele achado, não substituição dele) — e o texto
  do aviso editável.
- **Uma decisão pendente do dono**: se sessão em que o paciente falta é
  cobrada (linha `[decisão do dono]` acima). Nenhum dos dois lados deve ser
  escolhido pela engenharia.

---

# Travas — Fase 0.5 (Tasks 4, 5 e 6)

Tudo a partir daqui é da **Fase 0.5** (formulários, validação §6.2, texto do
aviso §4⑤ e estados vazios) — a tabela acima é só da Fase 0 e fica como
registro histórico, com as poucas linhas marcadas "RESOLVIDA" onde a Fase 0.5
fechou o que a Fase 0 tinha deixado em aberto. A tabela abaixo segue o mesmo
formato (Cenário | Onde | O que travou | Resolução); C5 e C6 são os cenários
novos desta fase (`prototipo/CENARIOS.md`).

As linhas de C5 (Task 4) e C6 (Task 5) foram transcritas de
`.superpowers/sdd/2026-09-02-fase-0-5-formularios/travas-a-registrar.md`, que
já trazia balde e resolução decididos por quem implementou cada tarefa — não
foram reclassificadas aqui, só copiadas para o registro único. As linhas de
Task 6 são achados desta própria tarefa.

| Cenário | Onde | O que travou | Resolução |
|---|---|---|---|
| C5 | Diálogo do paciente, bloco de canal | `#bloco-canal` tinha o atributo `hidden`, mas `.campo { display: flex }` vencia por especificidade e o bloco continuava visível | `[layout]` Corrigido com `.campo[hidden] { display: none }` em `dialogo.css`. Só apareceu em navegador — leitura de código não pega |
| C5 | `estilo.css`, status da ficha e de pacientes | `.status-ficha.ok` e `.status-pacientes.ok` não existiam; eram a única dupla de status sem a variante verde de sucesso que as outras três telas têm | `[layout]` Corrigido, variantes acrescentadas |
| C5 | `DADOS.pacientes` | Não guardava telefone, nascimento nem e-mail, então "Editar" abria os três campos em branco | `[fatia]` Reclassificado de `[escopo]` pelo controlador (a spec §5.4 tem esses campos; era o dado falso que estava mais pobre que o modelo) e conserto autorizado: os três campos entraram nos três pacientes |
| C5 | Diálogo do paciente, salvar edição | Editar e salvar descartava telefone/nascimento/e-mail em silêncio, porque o `aoEnviar` não persistia os campos novos | `[layout]` Corrigido — mesma família do defeito acima, achado pelo próprio implementador |
| C5 | `refletirCanal()` | Desmarcar o consentimento (ou trocar canal para NENHUM) depois de um erro de e-mail deixava o rótulo "opcional" reaparecer E a mensagem de erro visível ao mesmo tempo, com `aria-invalid` ainda setado | `[layout]` Corrigido expondo `revalidar(id)` na API do `ligarFormulario`, que repinta só se o campo já estava em erro. Não foi remendo de tela: validação condicional precisa dessa primitiva, e a Task 5 reusou. Confirmado em navegador |
| C6 | Fluxo de retorno, foco inicial | O foco caía em nada: o primeiro campo do diálogo é o seletor de paciente, que no retorno está desabilitado | `[layout]` Corrigido focando `#con-data` explicitamente |
| C6 | Confirmação do retorno | A mensagem de sucesso ia sempre para `#status-agenda`, que está invisível quando o diálogo foi aberto da Ficha | `[layout]` Corrigido roteando para `#status-registro` no fluxo de retorno |
| C6 | Campos de data e hora | As duas regras se leem uma a outra, mas o `ligarFormulario` só revalida o campo que o usuário toca | `[layout]` Corrigido com o `revalidar` que a Task 4 criou — mesma primitiva, segundo uso |
| C6 | Agenda, mecanismo "Provisório" | Marcar slot como provisório era gambiarra de protótipo, substituída pelo diálogo de verdade | `[layout]` Corrigido: mecanismo, CSS morto e a nota da tela removidos |
| C6 | Os três pontos de entrada de consulta | Nenhum escreve no estado real da Agenda; só mostram confirmação inline | `[fatia]` Mesma raiz da trava de integração entre telas que já existe (fonte única de dados, spec §5.3/§5.6). Trabalho da etapa em React |
| C6 | Agenda, seletor de paciente no slot | Trava `[fatia]` anterior ("a Agenda não sabe de qual paciente é o retorno") | **RESOLVIDA por esta tarefa** — o diálogo pergunta o paciente. Marcada como resolvida na linha antiga da tabela da Fase 0 (cenário C2, "Tela Agenda — slot provisório não sabe de qual paciente é o retorno"), não duplicada aqui |
| Task 6 | Tela Hoje — subtítulo (`#hoje-sub`) e cartão de destaque (`#cartao-proxima`) | Ambos eram markup estático: "terça, 1 de setembro · 6 sessões hoje" e "Próxima, em 20 minutos · 09:00 · Helena Vaz" nunca mudavam — com o conjunto vazio, mentiriam contando sessão e paciente que não existem | `[layout]` `pintarHoje()` agora calcula os dois a partir de `DADOS.hoje`: o subtítulo conta `sessoes.length` (com plural certo); o cartão de destaque acha a próxima sessão que ainda não passou e se esconde por inteiro quando não há nenhuma |
| Task 6 | Tela Pacientes — subtítulo (`#pacientes-sub`) e tela Financeiro — subtítulo (`#financeiro-sub`) | "3 cadastrados" e "agosto de 2026" eram texto fixo, dessincronizado de `DADOS.pacientes.length` e `DADOS.financeiro.mes` | `[layout]` Ambos agora computados em `pintarPacientes()`/`pintarFinanceiro()` |
| Task 6 | Financeiro — `#baixa-lote` e seu `<h2>` | Markup estático (4 sessões de Bruna/Ivan escritas na mão) — com o conjunto vazio continuaria na tela, afirmando uma semana passada que não existiu | `[layout]` Escondidos por inteiro com `hidden` quando o alternador liga o conjunto vazio; conferido em navegador que o `hidden` realmente venceu (não é `.campo`, não tem `display` concorrente) |
| Task 6 | Agenda — `+ Nova consulta` com `DADOS.pacientes` vazio | `#con-paciente` só tinha a opção muda "Escolha o paciente" — dava pra abrir o diálogo, mas não pra entender por que não dava pra escolher ninguém | `[layout]` `pintarSelecaoPaciente()` mostra "Nenhum paciente cadastrado ainda" quando a lista está vazia; a mensagem de erro do campo também muda para "Cadastre um paciente antes de agendar uma consulta." nesse caso |
| Task 6 | Ficha (`tela-ficha`) — cabeçalho (`#ficha-nome`/`#ficha-sub`/`#chip-aviso-email`), alcançável só por hash no conjunto vazio | Continua mostrando "Helena Vaz", "34 anos... 14 sessões... R$ 180,00" e o chip de consentimento mesmo com `DADOS.pacientes` vazio — mesma causa raiz já registrada na Fase 0 (roteamento por id de paciente é hardcode de protótipo, Fatia 2), só que agora visível também no conjunto vazio | `[fatia]` Não corrigida nesta tarefa: consertar exigiria desfazer o hardcode que a própria Fase 0 isolou como `[fatia]` (roteamento por `/pacientes/:id`), que é trabalho de Fatia 2, não de desenho de estado vazio. `#linha-tempo` e `#pendencias-paciente`, essas sim, ficam honestas (vazias e explicadas) |
| Task 6 | Ficha — retorno a partir de `#agendar-retorno` no conjunto vazio | `abrirRetorno('Helena Vaz', ...)` trava o select de paciente num nome que não existe em `DADOS.pacientes` (vazio) — mesma causa raiz da linha acima | `[fatia]` Mesma nota acima; só alcançável pela mesma rota hardcoded, não é caminho novo |
| Task 6 (item C) | Tela Agenda — botão "Série semanal" (`#serie-semanal`) | Único stub que sobra de verdade na Agenda: clicar sempre avisa "fora do escopo deste protótipo", nunca implementa recorrência | `[fatia]` Recorrência de consulta (criar N sessões repetidas a partir de uma) é trabalho de implementação de outra fatia, não de desenho — não contradiz a spec |
| Revisão da Task 6 | Ajustes — corpo do lembrete (`#aviso-texto`) e a prévia (`#previa-texto`) | **§7.1** O corpo virou `<textarea>` de texto livre, sem validação nenhuma de conteúdo — só um aviso na tela. A §7.1 exige "**Zero conteúdo clínico.** Nunca anotação, motivo ou pendência. **Só data, hora, e quem**", e essa exigência não está escopada ao assunto: os exemplos que abrem a seção citam "computador de casa" e "e-mail corporativo", que só fazem sentido com o e-mail aberto e lido. E **§5.2 não tem onde guardar esse texto**: `AvisoDeConsulta` é `consulta`, `destinatario`, `canal`, `agendadoPara`, `enviadoEm`, `estado`, `erro` — nenhum campo de corpo. O desenho da spec era uma mensagem derivada inteiramente da `Consulta`. Demonstrado em navegador pelo revisor: digitar `"Assunto: {paciente} tem terapia hoje"` põe a palavra "terapia" na prévia sem obstáculo nenhum | `[escopo]` **RESOLVIDA — decisão do dono, 4 de setembro de 2026**: opção (a) das duas que esta linha apontava — corpo montado a partir de blocos fixos e dos três marcadores. Spec §4⑤/§5.2/§5.5 atualizada primeiro. Implementado pela Task 7: `#aviso-texto` saiu; entram duas frases obrigatórias fixas (sem checkbox, sempre na prévia) e três opcionais com checkbox, sem textarea nem caminho para texto digitado chegar ao corpo — a garantia volta a ser estrutural |

### Diferidos (Fase 0 e Fase 0.5)

Minors que não entram na tabela acima — registrados, não corrigidos nesta
fase:

- Fase 0: `color: #fff` escrito à mão em `estilo.css` nos dois usos de
  `--acento` como fundo — candidato a virar token `--sobre-acento`
- Fase 0: "última sessão" é ambíguo quando a mais recente foi falta; o
  combinado aparece dois itens abaixo. Deixado de propósito, como pergunta de
  desenho para o dono
- Fase 0.5: o seletor de paciente desabilitado no fluxo de retorno não tem
  estilo `:disabled` próprio em lugar nenhum do protótipo
- Fase 0.5: "Diego Fontes", do cenário C6, não existe em `DADOS.pacientes` —
  a Ficha é hardcoded na Helena Vaz. Limitação pré-existente, já registrada
  como `[fatia]`
- Fase 0.5 (Task 6): a busca de Pacientes, com o conjunto vazio e algum texto
  digitado, mostra "Nenhum paciente com esse nome" em vez do estado vazio
  "de verdade" (não tem paciente nenhum) — tecnicamente verdadeiro (não existe
  paciente com aquele nome), mas a causa real é outra. Caso de borda raro
  (por que alguém buscaria com zero pacientes?), não corrigido
- ~~Fase 0.5 (Task 6): o corpo do lembrete (`#aviso-texto`) não é validado
  contra conteúdo clínico~~ — **promovido a `[escopo]` pela revisão da Task 6**
  e movido para a tabela da Fase 0.5, acima. Não é minor diferido: contradiz
  §7.1 e não tem onde ser guardado em §5.2. **Resolvido na Task 7** por decisão
  do dono (blocos fixos)
- Fase 0.5 (Task 7): `gap: 4px` escrito à mão onde `--gap-1` já existe com o
  mesmo valor. Não viola a constraint de tokens, que é escopada a cor, mas é
  inconsistente com o resto da folha
- Fase 0.5 (Task 7): `pintarPreviaEmail()` interpola em `innerHTML` sem escapar.
  Hoje é inofensivo — o que entra é constante fixa do próprio código, e o nome
  na prévia é um exemplo, não dado de paciente. **Fica registrado para a etapa
  em React**: o mesmo padrão portado ingenuamente, com nome real de paciente
  vindo da API, é vetor de XSS. Em React o `{}` já escapa por padrão; o risco
  é alguém traduzir isto para `dangerouslySetInnerHTML` por parecer equivalente

## Veredito — Fase 0.5

Sem meio-termo, respondendo às quatro perguntas na ordem pedida.

**A spec §6.2 saiu desta fase com desenho?** Sim, e é a peça mais sólida da
entrega. `#dialogo-paciente` (Task 4) e `#dialogo-consulta` (Task 5) aplicam
as quatro regras que a Fase 0 tinha deixado de fora por inteiro: erro só
depois do primeiro toque (`blur`) e sempre no `submit`; obrigatório/opcional
visível antes do erro (`.campo-opcional`, rótulo que aparece e some conforme
a regra muda — caso do e-mail condicional ao canal); foco no primeiro campo
inválido, confirmado em navegador — submeter o formulário em branco leva o
foco a `#pac-nome`, o primeiro dos três inválidos (`pac-nome`,
`pac-nascimento`, `pac-valor`); com o nome preenchido, ao `#pac-nascimento`,
que passa a ser o primeiro. (A revisão desta tarefa achou aqui uma frase
errada: dizia que o foco ia a `#pac-email` chamando-o de "o primeiro do
formulário", quando ele é o último. O comportamento sempre esteve certo; a
descrição, não.);
conflito de horário checado antes do envio (`refletirDataHora`) e data no
passado avisando sem bloquear (`Essa data já passou — a consulta será
registrada como retroativa.`, confirmado em navegador). O item extra A desta
tarefa fechou o único gap cosmético que sobrava: `<select>` agora ganha o
mesmo contorno vermelho que `<input>`/`<textarea>` já tinham. **Ressalva**: a
validação é só de front-end, sem servidor real por trás — §6.2 diz "servidor
é a autoridade, cliente é o atalho", e um protótipo estático não tem
servidor nenhum para ser essa autoridade. O que se mede aqui é a
*experiência* de validação (quando o erro aparece, para onde vai o foco, o
que a tela diz) — não a garantia de que um servidor real vá aplicar a mesma
regra. Isso só se confirma na Fatia 1, com a API de verdade.

**O formulário de paciente com consentimento existe?** Sim — `#dialogo-
paciente` (Task 4), com o checkbox de autorização, o canal (e-mail/nenhum) e
a validação condicional que destranca §4③: e-mail é opcional até o
psicólogo ligar o aviso por e-mail, e a partir daí vira obrigatório, exercida
pelo cenário C5 (Diego Fontes não lembra o próprio e-mail) e confirmada em
navegador nesta tarefa. **Ressalva que já estava registrada e continua de
pé**: o cabeçalho da Ficha (nome, "34 anos...", chip de consentimento) é
hardcoded para a Helena Vaz — o formulário existe e valida de verdade, mas
"editar a Helena" é a única superfície de fato exercitável; roteamento por
paciente é Fatia 2.

**O texto do aviso existe?** Sim, com uma ressalva que pesa. `#aviso-texto`
foi prototipado no cartão "Aviso de consulta": editável, com marcadores
`{paciente}`/`{data}`/`{hora}`, atualiza a prévia ao vivo (confirmado em
navegador, inclusive o caso de campo vazio caindo no texto padrão com aviso
visível — nunca fica em branco mudo). O assunto continua fixo e neutro
("Lembrete de agendamento"), e a linha de descadastro continua fora do
textarea, sempre no fim da prévia — confirmado em navegador que marcar
"Incluir meu título profissional" muda o remetente e nunca o assunto. Isso
quer dizer que a garantia de §7.1 permanece estrutural **para o assunto**. Para
o **corpo**, não: a tela avisa "não escreva aqui nada sobre o motivo da
consulta", e nada no código valida o que o psicólogo digita ali.

**Atualização — Task 7 (decisão do dono, 4 de setembro de 2026).** O parágrafo
acima descreve o estado que a Correção do veredito, logo abaixo, classificou
como `[escopo]`. O dono decidiu: corpo montado a partir de blocos fixos — a
opção que a Correção já apontava como saída (a). `#aviso-texto` saiu do
protótipo; entram duas frases obrigatórias, fixas e visíveis na tela (sem
checkbox, sempre na prévia), e três opcionais com checkbox
(`aviso-frase-remarcar`, ligada por padrão; `aviso-frase-chegada` e
`aviso-frase-duracao`, desligadas). Confirmado em navegador (Task 7): marcar
e desmarcar cada opcional atualiza a prévia na hora; as duas obrigatórias
aparecem em qualquer combinação, inclusive com as três opcionais desligadas;
nenhuma combinação altera o assunto. A garantia de §7.1 volta a ser
estrutural também para o corpo, não só para o assunto.

**Correção do veredito, pela revisão desta tarefa.** A primeira versão desta
resposta classificou isso como "trade-off previsto, não contradição", e não como
`[escopo]`. Estava errado, por dois motivos que a revisão sustentou e o
controlador conferiu:

1. **A §7.1 não escopa a regra ao assunto.** O terceiro dos seus quatro itens é
   "**Zero conteúdo clínico.** Nunca anotação, motivo ou pendência. **Só data,
   hora, e quem**" — regra sobre o aviso inteiro. Os exemplos que abrem a seção
   citam "computador de casa" e "e-mail corporativo", cenários de e-mail **aberto
   e lido**, não só de tela de bloqueio.
2. **A §5.2 não tem onde guardar texto livre.** `AvisoDeConsulta` é `consulta`,
   `destinatario`, `canal`, `agendadoPara`, `enviadoEm`, `estado`, `erro`. O
   desenho da spec era uma mensagem derivada inteiramente da `Consulta` —
   exatamente "só data, hora, e quem". O `<textarea>` não interpreta a §4⑤: ele
   inventa uma superfície que o modelo não prevê.

E não é teórico: a revisão digitou no campo `"Assunto: {paciente} tem terapia
hoje"` e a palavra "terapia" foi à prévia sem obstáculo nenhum.

O erro de raciocínio a registrar é o argumento de autoridade: o veredito
justificou a classificação dizendo que "o próprio brief desta tarefa autorizou
esse desenho". **Brief de tarefa não desautoriza um "nunca" da spec** — só o dono
do produto pode, e ele não foi consultado sobre abrir mão desta garantia. O brief
era meu, e escrevê-lo autorizando texto livre foi o defeito de origem.

**A Fatia 2 continua bloqueada, ou foi liberada? (Reescrito pela Task 7.)**
**Liberada.** A trava `[escopo]` que a bloqueava — linha "Revisão da Task 6",
`#aviso-texto` — foi resolvida: o dono decidiu, em 4 de setembro de 2026, que
o corpo do lembrete é montagem de blocos fixos, não texto livre. A spec
§4⑤/§5.2/§5.5 foi atualizada primeiro, e o protótipo passou a obedecer (ver
"Atualização — Task 7", acima). Reli a tabela inteira, as duas fases —
`grep -c '\[escopo\]'` e leitura de cada ocorrência — e sobrou zero linha
`[escopo]`: a única que existia era esta.

A outra trava desta tarefa, "Falta é cobrada" (tabela da Fase 0,
`[decisão do dono]`), também foi resolvida na mesma data: falta não é
cobrada. Ela não bloqueava a Fatia 2 por si — era `[decisão do dono]`, não
`[escopo]` — mas fechar as duas juntas é o que esta tarefa entrega, e as duas
têm a mesma natureza: escolha de negócio que só o dono podia fazer, não
conserto de engenharia.

Liberar não é dizer "pronto". Três ressalvas continuam de pé, nenhuma delas
resolvida por esta tarefa:

- **§6.2** — a validação dos dois formulários (`#dialogo-paciente`,
  `#dialogo-consulta`) é só de front-end. O protótipo estático não tem
  servidor nenhum para ser a autoridade que §6.2 exige; isso só se confirma
  na Fatia 1, com a API de verdade.
- **§4③** — a Ficha continua hardcoded para a Helena Vaz. O formulário existe
  e valida de verdade, mas "editar a Helena" é a única superfície de fato
  exercitável; roteamento por paciente (`/pacientes/:id`) é trabalho da
  Fatia 2.
- As treze linhas `[fatia]` já registradas (seis da Fase 0, sete da revisão
  final) continuam abertas — não são contradição de spec, são trabalho de
  implementação real que um protótipo estático não faz (fonte única de
  dados, arrastar para remarcar, estado vazio já resolvido em parte, etc.).

O resto se sustenta, e vale dizer o que se sustenta: os dois formulários com
§6.2 aplicado, a fusão consulta+anotação, o retorno com data, os estados
vazios das cinco telas, e o corpo do aviso agora estrutural para assunto **e**
corpo.

**As duas correções desta tarefa não têm a mesma força, e a revisão pegou o
veredito generalizando** (achado Important). A do corpo do aviso é estrutural
de verdade: o `<textarea>` saiu do DOM, e não há caminho para texto do
psicólogo chegar ao e-mail. A da falta **não é** — ela é a edição manual de
quatro números em `dados.js`. **Não existe no protótipo nenhuma função que
filtre por `status === 'realizada'` antes de somar dinheiro**; os únicos testes
de status que existem servem para rótulo e para o chip da ficha. O protótipo
mostra os números certos porque alguém os escreveu certos, não porque a regra
esteja implementada.

Isso não reabre a trava: a regra é estrutural **na spec** (§5.3), que é onde
ela precisa estar para a Fatia 3 nascer certa, e um protótipo com `financeiro`
como objeto literal não tem como derivar agregação de coisa nenhuma. Mas quem
ler este veredito precisa saber que a prova aqui é de desenho, não de código —
e que a Fatia 3 tem que implementar o filtro de verdade, ou o defeito volta. Como no veredito da Fase 0: isto mede que o protótipo não
afirma nada que a spec desminta — e agora, pela primeira vez nas duas fases,
mede isso sem nenhuma trava `[escopo]` pendente. Não é produto validado; é um
mapa de telas sem ponto vermelho aceso, com três ressalvas conhecidas e
endereçadas.
