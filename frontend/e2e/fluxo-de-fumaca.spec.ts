import { expect, test } from '@playwright/test'

/**
 * E-mail unico por execucao: o cadastro responde 409 em e-mail repetido, e o
 * teste roda contra um banco que persiste entre execucoes.
 */
function emailUnico(): string {
  return `fumaca-${Date.now()}@teste.local`
}

/** A API exige `@Future` na consulta, entao a data tem de ser calculada na hora. */
function daquiADias(dias: number): string {
  const data = new Date()
  data.setDate(data.getDate() + dias)
  return [
    data.getFullYear(),
    String(data.getMonth() + 1).padStart(2, '0'),
    String(data.getDate()).padStart(2, '0'),
  ].join('-')
}

test('registrar, entrar, cadastrar paciente, anotar, agendar e conferir a trilha', async ({
  page,
}) => {
  const email = emailUnico()
  const senha = 'senhaforte123'

  await page.goto('/login')

  await page.getByRole('button', { name: 'Criar conta' }).click()
  await page.getByLabel('Nome', { exact: true }).fill('Ana da Fumaça')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(senha)
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  // autenticar entra na aplicacao — a agenda do dia e a porta
  await expect(page.getByRole('heading', { name: 'Agenda' })).toBeVisible()

  await page.getByRole('link', { name: 'Pacientes' }).click()
  await expect(page.getByRole('heading', { name: 'Pacientes' })).toBeVisible()
  await page.getByRole('button', { name: 'Novo paciente' }).click()
  await page.getByLabel('Nome', { exact: true }).fill('Paciente da Fumaça')
  await page.getByLabel('Data de nascimento').fill('1991-04-12')
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  await page.getByRole('link', { name: /Paciente da Fumaça/ }).click()
  await expect(page.getByRole('heading', { name: 'Paciente da Fumaça' })).toBeVisible()

  const anotacao = 'Primeira sessão registrada pelo teste de fumaça.'
  await page.getByLabel('Anotação da sessão').fill(anotacao)
  await page.getByRole('button', { name: 'Registrar anotação' }).click()
  await expect(page.locator('ol li')).toHaveCount(1)
  await expect(page.getByText(anotacao)).toBeVisible()

  // lembrete: prova o PATCH de conclusao, que nenhum outro caminho exercita
  await page.getByRole('link', { name: 'Lembretes' }).click()
  await page.getByLabel('Novo lembrete').fill('Retomar o registro de sono.')
  await page.getByRole('button', { name: 'Adicionar lembrete' }).click()
  await page.getByRole('button', { name: 'Concluir' }).click()
  await expect(page.getByText('concluído')).toBeVisible()

  // agenda: prova o formato de LocalDateTime que o cliente monta
  const dia = daquiADias(7)
  await page.getByRole('link', { name: 'Agenda' }).click()
  await expect(page.getByRole('heading', { name: 'Agenda' })).toBeVisible()
  await page.getByRole('button', { name: 'Nova consulta' }).click()
  await page.getByLabel('Paciente').selectOption({ label: 'Paciente da Fumaça' })
  await page.getByLabel('Data', { exact: true }).fill(dia)
  await page.getByLabel('Hora', { exact: true }).fill('15:00')
  await page.getByRole('button', { name: 'Agendar' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)

  await page.getByLabel('De', { exact: true }).fill(dia)
  await page.getByLabel('Até', { exact: true }).fill(dia)
  await expect(page.getByText('agendada')).toBeVisible()

  // serie: prova o XOR do horizonte contra o validador de classe da API
  await page.getByRole('button', { name: 'Série semanal' }).click()
  await page.getByLabel('Paciente').selectOption({ label: 'Paciente da Fumaça' })
  await page.getByLabel('Primeira sessão').fill(daquiADias(14))
  await page.getByLabel('Hora', { exact: true }).fill('09:00')
  await page.getByLabel('Quantidade de sessões').fill('3')
  await expect(page.getByText('3 sessões às 09:00')).toBeVisible()
  await page.getByRole('button', { name: 'Criar série' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)

  await page.getByLabel('De', { exact: true }).fill(daquiADias(14))
  await page.getByLabel('Até', { exact: true }).fill(daquiADias(28))
  await expect(page.getByText('série').first()).toBeVisible()

  // a ficha ve as mesmas consultas pelo sub-recurso do paciente
  await page.getByRole('link', { name: 'Pacientes' }).click()
  await expect(page.getByRole('heading', { name: 'Pacientes' })).toBeVisible()
  await page.getByRole('link', { name: /Paciente da Fumaça/ }).click()
  await page.getByRole('link', { name: 'Consultas' }).click()
  await expect(page.getByText('4 consultas')).toBeVisible()

  // trilha: prova o PagedModel e que toda mutacao acima virou log
  await page.getByRole('link', { name: 'Trilha' }).click()
  await expect(page.getByRole('heading', { name: 'Trilha' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Registro' })).toBeVisible()
  await expect(page.getByText('criou').first()).toBeVisible()
})

/**
 * Guarda de contrato: a API responde `erros[{campo, erro}]`, e este projeto ja
 * leu esse campo como `mensagem` — com todos os mocks errados junto, entao a
 * suite inteira ficava verde enquanto, contra a API real, um 400 de validacao
 * nao mostrava absolutamente nada na tela. So um 400 de verdade pega isso.
 */
test('400 de validacao da API real marca o campo culpado', async ({ page }) => {
  await page.goto('/login')

  await page.getByRole('button', { name: 'Criar conta' }).click()
  await page.getByLabel('Nome', { exact: true }).fill('Ana da Fumaça')
  await page.getByLabel('E-mail').fill('isto-nao-e-um-email')
  await page.getByLabel('Senha').fill('senhaforte123')
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  await expect(page.getByText('formato de email invalido')).toBeVisible()
})
