import { expect, test } from '@playwright/test'

const NOME_DO_PACIENTE = 'Paciente Aviso'
const FRASE_DE_CHEGADA = 'Chegue com alguns minutos de antecedência.'

test('configura o aviso nos Ajustes, cadastra paciente com consentimento e ve o aviso autorizado na ficha', async ({
  page,
  request,
}) => {
  const email = `aviso-${Date.now()}@teste.local`
  const senha = 'senhaforte123'

  const registro = await request.post('/auth/registrar', {
    data: { nome: 'Psicologa do Aviso', email, senha },
  })
  expect(registro.ok(), `cadastro falhou: ${await registro.text()}`).toBeTruthy()

  await page.goto('/entrar')
  await page.getByLabel(/e-mail/i).fill(email)
  await page.getByLabel(/senha/i).fill(senha)
  await page.getByRole('button', { name: /^entrar$/i }).click()
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()

  await page.getByRole('navigation').getByRole('link', { name: 'Ajustes' }).click()
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible()
  await page.getByLabel('Antecedência', { exact: true }).selectOption({ label: '2 horas antes' })
  await page.getByLabel(FRASE_DE_CHEGADA, { exact: true }).check()
  await page.getByRole('button', { name: 'Salvar ajustes' }).click()
  await expect(page.getByRole('status')).toContainText('Ajustes salvos.')

  await page.getByRole('navigation').getByRole('link', { name: 'Hoje' }).click()
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()
  await page.getByRole('navigation').getByRole('link', { name: 'Ajustes' }).click()
  await expect(page.getByRole('heading', { name: 'Ajustes' })).toBeVisible()
  await expect(page.getByLabel('Antecedência', { exact: true })).toHaveValue('H2')
  await expect(page.getByLabel(FRASE_DE_CHEGADA, { exact: true })).toBeChecked()
  await expect(
    page.getByRole('region', { name: 'Como o aviso chega' }),
  ).toContainText(FRASE_DE_CHEGADA)

  await page.getByRole('navigation').getByRole('link', { name: 'Pacientes' }).click()
  await expect(page.getByRole('heading', { name: 'Pacientes' })).toBeVisible()
  await page.getByRole('button', { name: '+ Novo paciente' }).click()
  await expect(page.getByRole('heading', { name: 'Novo paciente' })).toBeVisible()
  await page.getByLabel('Nome').fill(NOME_DO_PACIENTE)
  await page.getByRole('textbox', { name: 'E-mail' }).fill(`paciente-${Date.now()}@teste.local`)
  await page.getByLabel('Paciente autorizou receber lembretes por e-mail').check()
  await page.getByRole('button', { name: 'Salvar' }).click()

  await page.getByRole('link', { name: NOME_DO_PACIENTE }).click()
  await expect(page.getByRole('heading', { name: NOME_DO_PACIENTE })).toBeVisible()
  await expect(page.getByText('Aviso autorizado')).toBeVisible()
})
