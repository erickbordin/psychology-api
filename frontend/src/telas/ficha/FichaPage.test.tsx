import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { FichaPage } from './FichaPage'

const ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'

const FICHA = {
  paciente: {
    idPaciente: ID,
    nome: 'Helena Vaz',
    email: null,
    telefone: '51998112345',
    dataNascimento: '1992-03-14',
    valorSessao: 180.0,
    canalAviso: 'NENHUM',
    consentimentoAvisoEm: null,
    createdAt: '2026-03-02T10:00:00',
  },
  linhaDoTempo: [
    { consultaId: 'c3', dtConsulta: '2026-12-02T09:00:00', status: 'AGENDADA', anotacoes: [] },
    {
      consultaId: 'c2',
      dtConsulta: '2026-11-25T09:00:00',
      status: 'REALIZADA',
      anotacoes: [
        { anotacaoId: 'a1', conteudo: 'Retomamos o combinado da semana.', createdAt: '2026-11-25T10:00:00' },
      ],
    },
    { consultaId: 'c1', dtConsulta: '2026-11-18T09:00:00', status: 'FALTOU', anotacoes: [] },
  ],
  pendencias: [
    { id: 'p1', descricao: 'Enviar recibo de novembro', concluido: false, createdAt: '2026-11-30T10:00:00' },
  ],
}

// `Record<string, unknown>`, nao `unknown` (defeito do plano — mesmo
// conserto ja registrado em `telas/hoje/HojePage.test.tsx`): `HttpResponse.json`
// exige `JsonBodyType`, e `unknown` cru nao satisfaz isso sem cast, que este
// plano ja proibiu.
const comFicha = (ficha: Record<string, unknown> = FICHA) =>
  servidor.use(http.get(`/pacientes/${ID}/ficha`, () => HttpResponse.json(ficha)))

// a tela le o id da rota: renderizar precisa entrar NA rota da ficha
// A tela le o id com `useParams()`, que so preenche com um `<Route>` casando
// acima na arvore. O `renderizar()` embrulha em `<MemoryRouter>` e nada mais,
// entao o `<Routes>` que a producao tem em `rotas.tsx` e montado aqui — o
// teste passa a exercitar o mesmo casamento de rota que roda de verdade, em
// vez de a producao fatiar o caminho a mao para caber num harness mais pobre.
const abrir = () =>
  renderizar(
    <Routes>
      <Route path="/pacientes/:pacienteId" element={<FichaPage />} />
    </Routes>,
    { rota: `/pacientes/${ID}` },
  )

describe('ficha do paciente', () => {
  it('cabecalho mostra nome e valor de sessao', async () => {
    comFicha()
    abrir()

    expect(await screen.findByRole('heading', { name: 'Helena Vaz' })).toBeInTheDocument()
    expect(screen.getByText(/180/)).toBeInTheDocument()
  })

  it('linha do tempo vem do encontro mais recente para o mais antigo', async () => {
    comFicha()
    abrir()

    const linhas = await screen.findAllByRole('listitem')
    expect(within(linhas[0]).getByText(/02\/12/)).toBeInTheDocument()
  })

  it('a anotacao aparece DENTRO da linha da sessao dela', async () => {
    comFicha()
    abrir()

    const texto = await screen.findByText(/retomamos o combinado/i)
    const linhaDaSessao = texto.closest('li')
    expect(within(linhaDaSessao!).getByText(/25\/11/)).toBeInTheDocument()
  })

  it('sessao sem anotacao nao finge ter uma', async () => {
    comFicha()
    abrir()

    const linhas = await screen.findAllByRole('listitem')
    expect(within(linhas[0]).queryByText(/retomamos/i)).not.toBeInTheDocument()
  })

  it('pendencias do paciente aparecem ao lado', async () => {
    comFicha()
    abrir()

    expect(await screen.findByText(/enviar recibo de novembro/i)).toBeInTheDocument()
  })

  it('paciente sem sessao nenhuma diz o que fazer', async () => {
    comFicha({ ...FICHA, linhaDoTempo: [], pendencias: [] })
    abrir()

    expect(await screen.findByText(/nenhuma sessao registrada/i)).toBeInTheDocument()
  })

  it('404 da API vira mensagem, nao tela quebrada', async () => {
    servidor.use(
      http.get(`/pacientes/${ID}/ficha`, () =>
        HttpResponse.json({ status: 404, mensagem: 'Paciente nao encontrado', erros: [] }, { status: 404 }),
      ),
    )
    abrir()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
