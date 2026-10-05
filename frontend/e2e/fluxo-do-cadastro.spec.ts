import { expect, test } from '@playwright/test'

import { ambiente } from './ambiente'

test('a psicologa cria a conta pelo convite, continua logada ao recarregar e sai', async ({
  page,
}) => {
  const mensagensDeErro: string[] = []
  page.on('console', (mensagem) => {
    if (mensagem.type() === 'error') mensagensDeErro.push(mensagem.text())
  })

  await page.goto('/entrar')
  await page.getByRole('link', { name: 'Criar conta' }).click()

  await page.getByLabel('Código de convite').fill(ambiente().convites[5])
  await page.getByLabel('Nome').fill('Psicologa do Cadastro')
  await page.getByLabel('E-mail').fill(`cadastro-${Date.now()}@teste.local`)
  await page.getByLabel('Senha').fill('senhaforte123')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Criar conta' }).click()

  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()

  await page.reload()
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()

  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page).toHaveURL(/\/entrar$/)

  console.log(`erros de console: ${JSON.stringify(mensagensDeErro)}`)
})
