import { expect, test } from '@playwright/test'

test('ajusta o expediente, ve a grade da agenda, a trilha, troca a senha e entra com a nova', async ({
  page,
  request,
}) => {
  const email = `ajustes-${Date.now()}@teste.local`
  const senha = 'senhaforte123'
  const senhaNova = 'outrasenha456'

  const registro = await request.post('/auth/registrar', {
    data: { nome: 'Psicologa dos Ajustes', email, senha },
  })
  expect(registro.ok(), `cadastro falhou: ${await registro.text()}`).toBeTruthy()

  await page.goto('/entrar')
  await page.getByLabel(/e-mail/i).fill(email)
  await page.getByLabel(/senha/i).fill(senha)
  await page.getByRole('button', { name: /^entrar$/i }).click()
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()

  await page.getByRole('navigation').getByRole('link', { name: 'Ajustes' }).click()
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible()
  await page.getByLabel('Início', { exact: true }).selectOption({ label: '08:00' })
  await page.getByLabel('Fim', { exact: true }).selectOption({ label: '18:00' })
  await page.getByRole('button', { name: 'Salvar ajustes' }).click()
  await expect(page.getByRole('status')).toContainText('Ajustes salvos.')

  await page.getByRole('navigation').getByRole('link', { name: 'Agenda' }).click()
  await expect(page.getByRole('rowheader', { name: '08:00' })).toBeVisible()
  await expect(page.getByRole('rowheader', { name: '17:00' })).toBeVisible()
  await expect(page.getByRole('rowheader', { name: '07:00' })).toHaveCount(0)
  await expect(page.getByRole('rowheader', { name: '18:00' })).toHaveCount(0)

  await page.getByRole('navigation').getByRole('link', { name: 'Ajustes' }).click()
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible()
  await expect(
    page.getByRole('list', { name: 'Trilha de acesso' }),
  ).toContainText('Alterou os ajustes')

  const abrirTroca = page.getByRole('button', { name: 'Trocar senha' })
  await abrirTroca.click()
  await expect(page.getByRole('dialog', { name: 'Trocar senha' })).toBeVisible()
  await page.getByRole('button', { name: 'Cancelar' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(abrirTroca).toBeFocused()

  await abrirTroca.click()
  const dialogo = page.getByRole('dialog', { name: 'Trocar senha' })
  await dialogo.getByLabel(/^Senha atual/).fill(senha)
  await dialogo.getByLabel(/^Nova senha/).fill(senhaNova)
  await dialogo.getByRole('button', { name: 'Salvar' }).click()
  await expect(page.getByRole('status')).toContainText(
    'Senha trocada. As outras sessões foram encerradas.',
  )

  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page).toHaveURL(/\/entrar/)
  await page.getByLabel(/e-mail/i).fill(email)
  await page.getByLabel(/senha/i).fill(senhaNova)
  await page.getByRole('button', { name: /^entrar$/i }).click()
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()
})
