import { expect, test } from '@playwright/test'

/**
 * O segundo dos quatro fluxos de 8.5: "a sessao" — abre ficha, registra
 * anotacao, agenda retorno. Ele prova o que nenhum dos 73 testes com MSW
 * prova: que `consultaId` viaja do agendamento ate a anotacao, e volta da API
 * REAL ligado a sessao certa (spec 5.1, ficha de pagina unica).
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

  // 4. agenda uma sessao — o select do dialogo agora traz o paciente de
  // verdade (Tarefa 6). A Agenda so mostra 07h-19h de segunda a domingo; a
  // celula de HOJE as 07h e a mais previsivel (nunca ocupada por outro
  // paciente, porque cada execucao usa um usuario novo).
  await page.getByRole('navigation').getByRole('link', { name: 'Agenda' }).click()
  await expect(page.getByRole('grid', { name: /semana/i })).toBeVisible()

  const hoje = new Date()
  // mesma formula de `diasDaSemana` em AgendaPage.tsx: 0=segunda...6=domingo
  const colunaDeHoje = (hoje.getDay() + 6) % 7
  const linhaDas7h = page.locator('table.agenda-grade tbody tr').filter({ hasText: '07:00' })
  await linhaDas7h.locator('td[role="gridcell"]').nth(colunaDeHoje).click()

  await expect(page.getByRole('heading', { name: 'Nova consulta' })).toBeVisible()
  // o <select> ja existe no DOM antes da lista de pacientes chegar — esperar
  // a option evita selecionar antes dela existir (mesma cautela do teste de
  // componente de DialogoDeConsulta.test.tsx).
  await expect(page.getByRole('option', { name: NOME_DO_PACIENTE })).toBeAttached()
  await page.getByLabel('Paciente').selectOption({ label: NOME_DO_PACIENTE })
  await page.getByRole('button', { name: 'Salvar' }).click()

  // esperar a celula ocupada, nao o fechamento do dialogo: prova que o POST
  // /consultas resolveu e a grade foi invalidada.
  await expect(page.getByRole('gridcell').filter({ hasText: NOME_DO_PACIENTE })).toBeVisible()

  // 5. marca a sessao como realizada — SO a tela Hoje tem esse botao (spec
  // 4-1), e SO para sessao de hoje (`/painel/hoje` filtra pelo dia). E por
  // isso que o passo 4 agendou para hoje, e nao para qualquer dia da semana.
  await page.getByRole('navigation').getByRole('link', { name: 'Hoje' }).click()
  const linhaDaSessaoHoje = page.getByRole('listitem', { name: `Sessao de ${NOME_DO_PACIENTE}` })
  await expect(linhaDaSessaoHoje).toBeVisible()

  await linhaDaSessaoHoje.getByRole('button', { name: 'Realizada' }).click()
  // esperar o chip mudar, nao o clique: prova que o PATCH /consultas/{id}/status
  // resolveu e o painel foi invalidado.
  await expect(linhaDaSessaoHoje.getByText('REALIZADA')).toBeVisible()

  // 6. escreve a anotacao NA LINHA daquela sessao. A caixa de registro so
  // aparece (LinhaDoTempo.tsx) para sessao REALIZADA e ainda sem anotacao —
  // se ela nao aparecer aqui, o passo 5 nao funcionou de verdade.
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

  const linhaDaSessaoNaFicha = page.locator('.linha-tempo li').filter({ hasText: TEXTO_DA_ANOTACAO })
  await expect(linhaDaSessaoNaFicha).toBeVisible()
  // a prova final: a anotacao voltou dentro da MESMA sessao (REALIZADA), nao
  // solta na ficha nem presa a outra linha da linha do tempo.
  await expect(linhaDaSessaoNaFicha).toContainText('REALIZADA')
})
