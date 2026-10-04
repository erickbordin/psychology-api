import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { clienteDeTeste, renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { PacientesPage } from './PacientesPage'

const HELENA = {
  idPaciente: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  nome: 'Helena Vaz',
  email: 'helena@example.com',
  telefone: '51998112345',
  dataNascimento: '1992-03-14',
  valorSessao: 180.0,
  canalAviso: 'NENHUM',
  consentimentoAvisoEm: null,
  descadastradoEm: null,
  createdAt: '2026-03-02T10:00:00',
}

const RUI = { ...HELENA, idPaciente: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', nome: 'Rui Sampaio' }

const comPacientes = (lista: unknown[] = [HELENA, RUI]) =>
  servidor.use(http.get('/pacientes', () => HttpResponse.json(lista)))

describe('tela de pacientes', () => {
  it('lista quem esta cadastrado', async () => {
    comPacientes()
    renderizar(<PacientesPage />)

    expect(await screen.findByText('Helena Vaz')).toBeInTheDocument()
    expect(screen.getByText('Rui Sampaio')).toBeInTheDocument()
  })

  it('busca manda o termo para a API', async () => {
    let recebido: string | null = null
    servidor.use(
      http.get('/pacientes', ({ request }) => {
        recebido = new URL(request.url).searchParams.get('nome')
        return HttpResponse.json([HELENA])
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<PacientesPage />)

    await usuario.type(await screen.findByLabelText(/buscar/i), 'Helena')

    // NAO ha debounce: a tela dispara uma requisicao por tecla, e o
    // TanStack cacheia por queryKey. Esperar o efeito (e nao cravar um tempo)
    // vale de qualquer forma — e o que torna o teste indiferente a essa
    // decisao, se ela mudar.
    expect(await screen.findByText('Helena Vaz')).toBeInTheDocument()
    await screen.findByText('Helena Vaz')
    expect(recebido).toBe('Helena')
  })

  it('dia 1 do psicologo: diz o que fazer, nao fica em branco', async () => {
    comPacientes([])
    renderizar(<PacientesPage />)

    expect(await screen.findByText(/nenhum paciente/i)).toBeInTheDocument()
  })

  it('busca sem resultado nao finge que o cadastro esta vazio', async () => {
    const usuario = userEvent.setup()
    comPacientes([])
    renderizar(<PacientesPage />)

    await usuario.type(await screen.findByLabelText(/buscar/i), 'Ninguem')

    expect(await screen.findByText(/nenhum paciente com esse nome/i)).toBeInTheDocument()
  })

  it('cada paciente leva para a ficha dele', async () => {
    comPacientes()
    renderizar(<PacientesPage />)

    const link = await screen.findByRole('link', { name: /helena vaz/i })
    expect(link).toHaveAttribute('href', `/pacientes/${HELENA.idPaciente}`)
  })

  it('o segundo cadastro comeca em branco, nao com os dados do primeiro', async () => {
    // O defeito C1: o dialogo montado incondicionalmente mantinha vivo o
    // `useForm` de dentro dele, e reabrir restaurava `_formValues` do paciente
    // ANTERIOR. Num cadastro clinico isso grava o telefone de uma pessoa na
    // ficha de outra, sem ninguem ver.
    servidor.use(
      http.post('/pacientes', () => HttpResponse.json({}, { status: 201 })),
      http.get('/pacientes', () => HttpResponse.json([HELENA])),
    )
    const usuario = userEvent.setup()
    renderizar(<PacientesPage />)

    await usuario.click(await screen.findByRole('button', { name: /novo paciente/i }))
    await usuario.type(screen.getByLabelText(/nome/i), 'Helena Vaz')
    await usuario.type(screen.getByLabelText(/telefone/i), '51998112345')
    await usuario.click(screen.getByRole('button', { name: /salvar/i }))

    // o dialogo so fecha depois que o POST resolve — esperar o sumico dele e
    // esperar a gravacao, sem cravar tempo
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /salvar/i })).not.toBeInTheDocument(),
    )

    await usuario.click(screen.getByRole('button', { name: /novo paciente/i }))

    expect(screen.getByLabelText(/nome/i)).toHaveValue('')
    expect(screen.getByLabelText(/telefone/i)).toHaveValue('')
    expect(screen.getByLabelText(/e-mail/i, { selector: 'input[type="email"]' })).toHaveValue('')
    expect(screen.getByLabelText(/valor/i)).toHaveValue(null)
    expect(screen.getByLabelText('Paciente autorizou receber lembretes por e-mail')).not.toBeChecked()
  })

  it('limpar a busca nao acusa cadastro vazio com o resultado do termo anterior', async () => {
    // M2. O quadro em que a tela mentia: o termo buscado nao achou ninguem, o
    // usuario LIMPA a caixa, e a lista sem filtro ainda nao voltou. Nesse
    // quadro `buscando` ja e false (le a caixa) mas `data` ainda e o
    // `placeholderData` do termo anterior — lista vazia — e a tela anunciava
    // "Nenhum paciente cadastrado ainda." com o botao "Cadastrar o primeiro"
    // para quem tem dois pacientes.
    //
    // `gcTime: 0` e o que torna o quadro DETERMINISTICO: ele expressa "a
    // listagem sem filtro nao esta mais no cache", que em producao acontece
    // sozinho (o padrao do TanStack descarta a entrada sem observador em 5
    // min de busca). Com a entrada em cache o TanStack devolve o dado antigo
    // e nao ha placeholder — o defeito existe justamente quando nao ha.
    const respostas: Record<string, unknown[]> = { '': [HELENA, RUI], Ninguem: [] }
    servidor.use(
      http.get('/pacientes', async ({ request }) => {
        const nome = new URL(request.url).searchParams.get('nome') ?? ''
        // a lista sem filtro demora: e exatamente durante essa espera que a
        // tela decide o que dizer
        if (nome === '') await delay(60)
        return HttpResponse.json(respostas[nome] ?? [])
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<PacientesPage />, { cliente: clienteDeTeste({ defaultOptions: { queries: { gcTime: 0 } } }) })

    const caixa = await screen.findByLabelText(/buscar/i)
    await usuario.type(caixa, 'Ninguem')
    expect(await screen.findByText(/nenhum paciente com esse nome/i)).toBeInTheDocument()

    await usuario.clear(caixa)

    // enquanto a lista sem filtro viaja, NENHUM dos dois estados vazios e
    // afirmavel — o dado na tela e de outro termo
    expect(screen.queryByText(/nenhum paciente cadastrado ainda/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /cadastrar o primeiro/i })).not.toBeInTheDocument()

    // e quando ela chega, a lista de verdade aparece
    expect(await screen.findByText('Rui Sampaio')).toBeInTheDocument()
  })

  it('a linha mostra idade, contato e valor, nao so o nome', async () => {
    comPacientes()
    renderizar(<PacientesPage />)
    const linha = (await screen.findByText('Helena Vaz')).closest('li')!
    expect(within(linha).getByText(/anos/)).toBeInTheDocument()
    expect(within(linha).getByText(/180/)).toBeInTheDocument()
  })

  it('o chip de consentimento fica na linha, fora do link', async () => {
    comPacientes()
    renderizar(<PacientesPage />)
    const linha = (await screen.findByText('Helena Vaz')).closest('li')!
    const chip = within(linha).getByText(/aviso (nao )?autorizado/i)
    expect(chip).toBeInTheDocument()
    expect(chip.closest('a')).toBeNull()
  })

  it('sob privacidade o nome e o metadado borram, o consentimento nao', async () => {
    comPacientes()
    renderizar(<PacientesPage />, { privacidadeLigada: true })

    const linha = (await screen.findByText('Helena Vaz')).closest('li')!
    expect(within(linha).getByText('Helena Vaz')).toHaveAttribute('data-borrado', 'true')
    expect(
      within(linha).getByText(/aviso (nao )?autorizado/i).closest('[data-borrado="true"]'),
    ).toBeNull()
  })

  it('paciente sem telefone e sem valor nao mostra separador orfao', async () => {
    servidor.use(
      http.get('/pacientes', () =>
        HttpResponse.json([{ ...HELENA, telefone: null, email: null, valorSessao: null }]),
      ),
    )
    renderizar(<PacientesPage />)
    const linha = (await screen.findByText('Helena Vaz')).closest('li')!
    expect(within(linha).queryByText(/·\s*·/)).toBeNull()
    expect(linha.textContent).not.toMatch(/·\s*$/)
  })
})
