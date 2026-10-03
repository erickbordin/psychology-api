import { expect, test } from '@playwright/test'

const NOME_DO_PACIENTE = 'Paciente Dinheiro'
const HORA_DA_SESSAO = '07:00'

test('realiza a sessao, ve o debito no Financeiro, da baixa em lote e desfaz', async ({
  page,
  request,
}) => {
  const email = `dinheiro-${Date.now()}@teste.local`
  const senha = 'senhaforte123'

  const registro = await request.post('/auth/registrar', {
    data: { nome: 'Psicologa do Dinheiro', email, senha },
  })
  expect(registro.ok(), `cadastro falhou: ${await registro.text()}`).toBeTruthy()

  await page.goto('/entrar')
  await page.getByLabel(/e-mail/i).fill(email)
  await page.getByLabel(/senha/i).fill(senha)
  await page.getByRole('button', { name: /^entrar$/i }).click()
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()

  await page.getByRole('navigation').getByRole('link', { name: 'Pacientes' }).click()
  await expect(page.getByRole('heading', { name: 'Pacientes' })).toBeVisible()
  await page.getByRole('button', { name: '+ Novo paciente' }).click()
  await expect(page.getByRole('heading', { name: 'Novo paciente' })).toBeVisible()
  await page.getByLabel('Nome').fill(NOME_DO_PACIENTE)
  await page.getByLabel('Valor da sessao').fill('150')
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(page.getByRole('link', { name: NOME_DO_PACIENTE })).toBeVisible()

  await page.getByRole('navigation').getByRole('link', { name: 'Agenda' }).click()
  await expect(page.getByRole('grid', { name: /semana/i })).toBeVisible()
  const colunaDeHoje = (new Date().getDay() + 6) % 7
  const linhaDoHorario = page
    .locator('table.agenda-grade tbody tr')
    .filter({ hasText: HORA_DA_SESSAO })
  await linhaDoHorario.locator('td[role="gridcell"]').nth(colunaDeHoje).click()

  const dialogo = page.getByRole('dialog')
  await expect(dialogo.getByRole('heading', { name: 'Nova consulta' })).toBeVisible()
  await expect(dialogo.getByRole('option', { name: NOME_DO_PACIENTE })).toBeAttached()
  await dialogo.getByLabel('Paciente').selectOption({ label: NOME_DO_PACIENTE })
  await dialogo.getByRole('button', { name: 'Salvar' }).click()
  await expect(
    linhaDoHorario
      .locator('td[role="gridcell"]')
      .nth(colunaDeHoje)
      .filter({ hasText: NOME_DO_PACIENTE }),
  ).toBeVisible()

  await page.getByRole('navigation').getByRole('link', { name: 'Hoje' }).click()
  const linhaDeHoje = page.getByRole('listitem', { name: `Sessao de ${NOME_DO_PACIENTE}` })
  await expect(linhaDeHoje).toBeVisible()
  await linhaDeHoje.getByRole('button', { name: 'Realizada' }).click()
  await expect(linhaDeHoje).toContainText('dê baixa no pagamento')

  await page.getByRole('navigation').getByRole('link', { name: 'Financeiro' }).click()
  await expect(page.getByRole('heading', { name: 'Financeiro' })).toBeVisible()

  const totais = page.getByRole('region', { name: 'Totais' })
  await expect(totais).toContainText(/Faturado\s*R\$\s*150,00/)
  await expect(totais).toContainText(/A receber\s*R\$\s*150,00/)

  const devendo = page.getByRole('region', { name: 'Quem está devendo' })
  await expect(devendo).toContainText(NOME_DO_PACIENTE)

  await devendo.getByRole('button', { name: 'Dar baixa em todas (1)' }).click()
  await expect(page.getByRole('status')).toContainText(
    `1 sessão de ${NOME_DO_PACIENTE} com baixa.`,
  )
  await expect(devendo).toContainText('Ninguém devendo')
  await expect(totais).toContainText(/Recebido\s*R\$\s*150,00/)

  const sessoesDoMes = page.getByRole('region', { name: 'Sessões do mês' })
  await sessoesDoMes.getByRole('button', { name: 'Desfazer baixa' }).click()
  await expect(devendo).toContainText(NOME_DO_PACIENTE)
  await expect(devendo).not.toContainText('Ninguém devendo')
})
