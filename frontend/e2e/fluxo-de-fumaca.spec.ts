import { expect, test } from '@playwright/test'

/**
 * Prova o que nenhum dos 41 testes com MSW prova: que a resposta que a tela
 * espera e a que a API REAL da. Backend no ar em localhost:8080 e um usuario
 * novo por execucao (registrado pela propria API) sao os unicos pre-requisitos
 * — o banco nao tem usuario semeado (recriado do zero na Task 8, 10 tabelas,
 * zero linhas), e o cadastro responde 409 em e-mail repetido, entao um e-mail
 * fixo colidiria numa segunda execucao contra o mesmo banco.
 */
function emailUnico(): string {
  return `fumaca-${Date.now()}@teste.local`
}

test('o psicologo entra, ve o dia, agenda e marca presenca', async ({ page, request }) => {
  const email = emailUnico()
  const senha = 'senhaforte123'

  // Esta fatia nao tem tela de cadastro (rotas.tsx so declara /entrar) — o
  // registro vai direto na API, pelo mesmo proxy que o app usa (baseURL da
  // config aponta pro Vite, que espelha /auth pro backend). `POST
  // /auth/registrar` pede `{ nome, email, senha }`; a senha precisa passar
  // pelas duas regras que o servidor aplica: 10+ caracteres e fora da lista
  // de senhas comuns versionada.
  const registro = await request.post('/auth/registrar', {
    data: { nome: 'Psicologa da Fumaca', email, senha },
  })
  expect(registro.ok(), `cadastro falhou: ${await registro.text()}`).toBeTruthy()

  await page.goto('/entrar')
  await page.getByLabel(/e-mail/i).fill(email)
  await page.getByLabel(/senha/i).fill(senha)
  await page.getByRole('button', { name: /^entrar$/i }).click()

  // /entrar so navega para "/" apos o POST /auth/login resolver — aguardar a
  // tela e nao o clique evita corrida com o `RotaProtegida`, que decide pelo
  // estado (ainda em memoria) do SessaoProvider.
  await expect(page.getByRole('heading', { name: /^hoje$/i })).toBeVisible()

  // dia 1 do psicologo pagante (spec 9): usuario novo, zero sessoes — a tela
  // tem que dizer o que fazer, nao quebrar nem ficar em branco.
  await expect(page.getByText(/nenhuma sessao hoje/i)).toBeVisible()

  // navegar pelo ROTULO ESCRITO: a navegacao tem cinco destinos com nome (spec
  // 4), e usar o rotulo em vez de um seletor de rota e o que prova isso.
  await page.getByRole('navigation').getByRole('link', { name: 'Agenda' }).click()
  await expect(page.getByRole('grid', { name: /semana/i })).toBeVisible()
})
