import { expect, test } from '@playwright/test'

/**
 * O segundo dos quatro fluxos de 8.5: "a sessao" — abre ficha, registra
 * anotacao, agenda retorno. Ele prova o que nenhum dos testes com MSW prova:
 * que `consultaId` viaja do agendamento ate a anotacao, e volta da API REAL
 * ligado a sessao certa (spec 5.1, ficha de pagina unica).
 *
 * **Por que DUAS sessoes.** Ate a revisao, o fluxo criava UMA sessao e depois
 * afirmava que a anotacao voltou "dentro da linha certa" — o que era verdade
 * por construcao: com uma linha so na ficha, qualquer vinculo que o backend
 * fizesse (ou nao fizesse) deixava o teste verde. Se o servidor prendesse a
 * anotacao a outra consulta do MESMO paciente, ninguem ficaria sabendo. Agora
 * sao duas sessoes no mesmo dia, so a primeira e marcada REALIZADA e anotada, e
 * o teste cobra os DOIS lados: o texto esta na linha da sessao A **e nao esta**
 * na linha da sessao B. A asserção negativa e a que da valor a positiva.
 *
 * Os horarios sao 07h e 08h de propósito: a janela de conflito do backend e de
 * +/-50 minutos ao redor de `dtConsulta` (`ConsultaService`), entao 60 minutos
 * de distancia e o menor passo que cabe na grade (de hora em hora) sem a
 * segunda consulta ser recusada com 409.
 *
 * Mesmos pre-requisitos do `fluxo-de-fumaca.spec.ts`: backend no ar em
 * localhost:8080 e um usuario novo por execucao — o banco nao tem usuario
 * semeado, e o cadastro responde 409 em e-mail repetido.
 *
 * Dado falso e inventado, nunca real (LGPD): nome de paciente, e-mail e
 * anotacao abaixo nao correspondem a nenhuma pessoa nem a conteudo clinico —
 * so provam que o texto digitado num lugar volta no outro.
 */
function emailUnico(): string {
  return `sessao-${Date.now()}@teste.local`
}

const NOME_DO_PACIENTE = 'Paciente Um'
const TEXTO_DA_ANOTACAO = 'Nota de teste gerada pelo fluxo automatizado, sem conteudo clinico real.'
/** A sessao que recebe a anotacao, e a que NAO pode recebe-la. */
const HORA_DA_SESSAO_A = '07:00'
const HORA_DA_SESSAO_B = '08:00'

test('abre a ficha, agenda, marca presenca e a anotacao volta na sessao certa', async ({
  page,
  request,
}) => {
  const email = emailUnico()
  const senha = 'senhaforte123'

  // 1. registra o usuario pela API (mesmo caminho do fluxo de fumaca) e entra
  // pela tela.
  const registro = await request.post('/auth/registrar', {
    data: { nome: 'Psicologa da Sessao', email, senha },
  })
  expect(registro.ok(), `cadastro falhou: ${await registro.text()}`).toBeTruthy()

  await page.goto('/entrar')
  await page.getByLabel(/e-mail/i).fill(email)
  await page.getByLabel(/senha/i).fill(senha)
  await page.getByRole('button', { name: /^entrar$/i }).click()

  // esperar a tela e nao o clique: /entrar so navega apos o POST /auth/login
  // resolver.
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()

  // 2. vai em Pacientes (rotulo escrito, nao URL) e cadastra pelo dialogo.
  await page.getByRole('navigation').getByRole('link', { name: 'Pacientes' }).click()
  await expect(page.getByRole('heading', { name: 'Pacientes' })).toBeVisible()

  await page.getByRole('button', { name: '+ Novo paciente' }).click()
  await expect(page.getByRole('heading', { name: 'Novo paciente' })).toBeVisible()

  // `{ exact: true }` NAO serve aqui: o rotulo "Nome" tem um `*` visual dentro
  // de um `<span aria-hidden>` (Campo.tsx) — a arvore de acessibilidade exclui
  // esse `*` do NOME acessivel ("Nome"), mas o `getByLabel` do Playwright casa
  // pelo TEXTO CRU do `<label>` ("Nome *") quando `exact` esta ligado, e da 0
  // resultado. Confirmado isolando o seletor antes de escrever o resto do
  // fluxo. Sem `exact`, "Nome" e substring unica no dialogo — 1 resultado.
  await page.getByLabel('Nome').fill(NOME_DO_PACIENTE)
  await page.getByRole('button', { name: 'Salvar' }).click()

  // esperar o link aparecer na lista: prova que o POST /pacientes resolveu e
  // a lista foi invalidada, nao que o dialogo fechou.
  const linkDoPaciente = page.getByRole('link', { name: NOME_DO_PACIENTE })
  await expect(linkDoPaciente).toBeVisible()

  // 3. abre a ficha dele PELO LINK da lista.
  await linkDoPaciente.click()
  await expect(page.getByRole('heading', { name: NOME_DO_PACIENTE })).toBeVisible()
  await expect(page.getByText('Nenhuma sessao registrada ainda.')).toBeVisible()

  // 4. agenda DUAS sessoes no mesmo dia — o select do dialogo traz o paciente
  // de verdade (Tarefa 6). A Agenda so mostra 07h-19h de segunda a domingo; as
  // celulas de HOJE as 07h e as 08h sao as mais previsiveis (nunca ocupadas por
  // outro paciente, porque cada execucao usa um usuario novo).
  await page.getByRole('navigation').getByRole('link', { name: 'Agenda' }).click()
  await expect(page.getByRole('grid', { name: /semana/i })).toBeVisible()

  const hoje = new Date()
  // mesma formula de `diasDaSemana` em AgendaPage.tsx: 0=segunda...6=domingo
  const colunaDeHoje = (hoje.getDay() + 6) % 7

  async function agendar(horario: string) {
    const linha = page.locator('table.agenda-grade tbody tr').filter({ hasText: horario })
    await linha.locator('td[role="gridcell"]').nth(colunaDeHoje).click()

    const dialogo = page.getByRole('dialog')
    await expect(dialogo.getByRole('heading', { name: 'Nova consulta' })).toBeVisible()
    // o <select> ja existe no DOM antes da lista de pacientes chegar — esperar
    // a option evita selecionar antes dela existir (mesma cautela do teste de
    // componente de DialogoDeConsulta.test.tsx).
    await expect(dialogo.getByRole('option', { name: NOME_DO_PACIENTE })).toBeAttached()
    // ESCOPADO NO DIALOGO, e nao `page.getByLabel('Paciente')`: achado ao
    // agendar a SEGUNDA sessao. Depois da primeira, a grade tem uma celula
    // ocupada com `aria-label="Consulta de Paciente Um as ..."`, e "Paciente"
    // e substring dela — o seletor de pagina inteira passou a resolver dois
    // elementos e o Playwright recusou por strict mode. O dialogo e o escopo
    // certo: e dentro dele que o rotulo "Paciente" identifica um controle.
    await dialogo.getByLabel('Paciente').selectOption({ label: NOME_DO_PACIENTE })
    await dialogo.getByRole('button', { name: 'Salvar' }).click()

    // esperar a celula ocupada, nao o fechamento do dialogo: prova que o POST
    // /consultas resolveu e a grade foi invalidada. `filter` pela LINHA do
    // horario, senao a segunda espera casaria com a celula da primeira e
    // passaria antes de a segunda existir.
    await expect(
      linha.locator('td[role="gridcell"]').nth(colunaDeHoje).filter({ hasText: NOME_DO_PACIENTE }),
    ).toBeVisible()
  }

  await agendar(HORA_DA_SESSAO_A)
  await agendar(HORA_DA_SESSAO_B)

  // 5. marca SO a sessao A como realizada — SO a tela Hoje tem esse botao (spec
  // 4-1), e SO para sessao de hoje (`/painel/hoje` filtra pelo dia). E por
  // isso que o passo 4 agendou para hoje, e nao para qualquer dia da semana.
  // As duas linhas tem o MESMO nome acessivel ("Sessao de <paciente>"), entao o
  // horario e o que as separa — e e o mesmo criterio usado no passo 7.
  await page.getByRole('navigation').getByRole('link', { name: 'Hoje' }).click()
  const linhaDeHoje = (horario: string) =>
    page
      .getByRole('listitem', { name: `Sessao de ${NOME_DO_PACIENTE}` })
      .filter({ hasText: horario })

  await expect(linhaDeHoje(HORA_DA_SESSAO_A)).toBeVisible()
  await expect(linhaDeHoje(HORA_DA_SESSAO_B)).toBeVisible()

  await linhaDeHoje(HORA_DA_SESSAO_A).getByRole('button', { name: 'Realizada' }).click()
  // esperar o chip mudar, nao o clique: prova que o PATCH /consultas/{id}/status
  // resolveu e o painel foi invalidado.
  await expect(linhaDeHoje(HORA_DA_SESSAO_A).getByText('REALIZADA')).toBeVisible()
  // a sessao B continua AGENDADA: sem isto, um PATCH que marcasse a consulta
  // errada (ou as duas) passaria despercebido ja aqui.
  await expect(linhaDeHoje(HORA_DA_SESSAO_B).getByText('AGENDADA')).toBeVisible()

  // 6. escreve a anotacao NA LINHA daquela sessao. A caixa de registro so
  // aparece (LinhaDoTempo.tsx) para sessao REALIZADA e ainda sem anotacao —
  // por isso ha exatamente UMA caixa na ficha, a da sessao A, mesmo com duas
  // sessoes na linha do tempo. Se ela nao aparecer aqui (ou se aparecerem
  // duas), o passo 5 nao funcionou de verdade.
  await page.getByRole('navigation').getByRole('link', { name: 'Pacientes' }).click()
  await page.getByRole('link', { name: NOME_DO_PACIENTE }).click()
  await expect(page.getByRole('heading', { name: NOME_DO_PACIENTE })).toBeVisible()

  // rotulo COM acento — "Anotação" de verdade, nao "Anotacao" sem ele.
  await expect(page.getByLabel('Anotação')).toBeVisible()
  await page.getByLabel('Anotação').fill(TEXTO_DA_ANOTACAO)
  await page.getByRole('button', { name: 'Registrar' }).click()

  // esperar o texto aparecer, nao o clique: prova que o POST
  // /pacientes/{id}/anotacoes resolveu e a ficha foi invalidada.
  await expect(page.getByText(TEXTO_DA_ANOTACAO)).toBeVisible()

  // 7. "recarrega" a ficha e confere que a anotacao volta DENTRO da sessao
  // certa. Nao e `page.reload()`: o token de acesso vive so em memoria do
  // modulo (`src/api/cliente.ts`), sem hidratacao no boot, e recarregar a aba
  // hoje desloga (divida conhecida e assumida da Fatia 1, listada em "Fora de
  // escopo" do plano). Navegar para longe e voltar pelo rotulo da lista
  // desmonta e remonta a FichaPage, forcando um GET novo — o mesmo round trip
  // que um F5 faria, sem esbarrar na divida de auth que nao e desta fatia.
  await page.getByRole('navigation').getByRole('link', { name: 'Pacientes' }).click()
  await page.getByRole('link', { name: NOME_DO_PACIENTE }).click()
  await expect(page.getByRole('heading', { name: NOME_DO_PACIENTE })).toBeVisible()

  // a lista pelo ROTULO ESCRITO ("Sessoes", o mesmo do <h2> acima dela), nao
  // por `.linha-tempo`: a convencao da casa proibe navegar por `className`, e
  // o seletor antigo quebraria numa renomeacao de classe puramente visual.
  const sessoesDaFicha = page.getByRole('list', { name: 'Sessoes' }).getByRole('listitem')
  const sessaoA = sessoesDaFicha.filter({ hasText: HORA_DA_SESSAO_A })
  const sessaoB = sessoesDaFicha.filter({ hasText: HORA_DA_SESSAO_B })

  await expect(sessoesDaFicha).toHaveCount(2)

  // a prova final, e o par de asserções que a versao de uma sessao so nao
  // conseguia fazer: a anotacao voltou dentro da sessao A (a REALIZADA, a que
  // foi anotada) e NAO vazou para a sessao B, que segue AGENDADA e sem nota.
  await expect(sessaoA).toContainText(TEXTO_DA_ANOTACAO)
  await expect(sessaoA).toContainText('REALIZADA')
  await expect(sessaoB).not.toContainText(TEXTO_DA_ANOTACAO)
  await expect(sessaoB).toContainText('AGENDADA')
})
