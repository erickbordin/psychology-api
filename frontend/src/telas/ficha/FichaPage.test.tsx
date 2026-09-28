import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { FichaPage } from './FichaPage'

const ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'

// UUIDs de verdade em TODO id do fixture (M5 da revisao). `c1`/`a1`/`p1`
// economizavam digitacao e custavam a unica coisa que a Fatia 1 comprou ao
// gerar os tipos do contrato: a distancia entre o que o teste finge e o que a
// API manda. Um `consultaId` de duas letras passa por qualquer coisa — um UUID
// de verdade quebra na hora em que alguem fatiar, formatar ou comparar o id
// achando que e curto.
const CONSULTA_1 = 'c1a2b3c4-0001-4a1b-8c2d-000000000001'
const CONSULTA_2 = 'c1a2b3c4-0002-4a1b-8c2d-000000000002'
const CONSULTA_3 = 'c1a2b3c4-0003-4a1b-8c2d-000000000003'
const ANOTACAO_1 = 'a9b8c7d6-0001-4e5f-9a1b-000000000001'
const PENDENCIA_1 = 'f1e2d3c4-0001-4b5a-9c8d-000000000001'

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
    { consultaId: CONSULTA_3, dtConsulta: '2026-12-02T09:00:00', status: 'AGENDADA', anotacoes: [] },
    {
      consultaId: CONSULTA_2,
      dtConsulta: '2026-11-25T09:00:00',
      status: 'REALIZADA',
      anotacoes: [
        { anotacaoId: ANOTACAO_1, conteudo: 'Retomamos o combinado da semana.', createdAt: '2026-11-25T10:00:00' },
      ],
    },
    { consultaId: CONSULTA_1, dtConsulta: '2026-11-18T09:00:00', status: 'FALTOU', anotacoes: [] },
  ],
  pendencias: [
    { id: PENDENCIA_1, descricao: 'Enviar recibo de novembro', concluido: false, createdAt: '2026-11-30T10:00:00' },
  ],
}

/** A lista da linha do tempo, achada pelo ROTULO ESCRITO ("Sessoes", o mesmo
 *  do <h2> acima dela) e nunca por `className` — convencao da casa. Sem isto,
 *  `findAllByRole('listitem')` varre a linha do tempo E as pendencias do aside,
 *  e a asserção fala de uma lista que o teste nao escolheu. */
const linhaDoTempo = () => screen.findByRole('list', { name: 'Sessoes' })

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

  // M4 da revisao: este teste se chamava "linha do tempo vem do encontro mais
  // recente para o mais antigo" e nao testava isso. Quem ordena e o SERVIDOR
  // (`FichaService`); o componente desenha `sessoes` na ordem em que recebeu.
  // Renomeado em vez de "consertado" com um fixture embaralhado: para o teste
  // antigo ficar honesto seria preciso ORDENAR no cliente, e ai duas camadas
  // passariam a decidir a ordem — a do servidor viraria a ordem que ninguem
  // mais verifica, e um dia elas discordariam em silencio. O que o componente
  // deve a esta tela e nao reordenar, nao perder e nao misturar linha nenhuma;
  // e isso que o nome novo promete e as tres asserções cobram. A ordenacao de
  // verdade e responsabilidade do backend e tem teste la.
  it('a linha do tempo desenha todas as sessoes, na ordem em que a API mandou', async () => {
    comFicha()
    abrir()

    const linhas = within(await linhaDoTempo()).getAllByRole('listitem')

    expect(linhas).toHaveLength(3)
    expect(within(linhas[0]).getByText(/02\/12/)).toBeInTheDocument()
    expect(within(linhas[1]).getByText(/25\/11/)).toBeInTheDocument()
    expect(within(linhas[2]).getByText(/18\/11/)).toBeInTheDocument()
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

    const linhas = within(await linhaDoTempo()).getAllByRole('listitem')
    expect(within(linhas[0]).queryByText(/retomamos/i)).not.toBeInTheDocument()
  })

  it('pendencias do paciente aparecem ao lado', async () => {
    comFicha()
    abrir()

    expect(await screen.findByText(/enviar recibo de novembro/i)).toBeInTheDocument()
  })

  it('a faixa de pendencias diz quantas sao', async () => {
    comFicha()
    abrir()

    const faixa = await screen.findByRole('complementary', { name: 'Pendencias' })
    expect(within(faixa).getByText('1')).toBeInTheDocument()
  })

  it('a linha do tempo nao divide mais a largura com as pendencias', async () => {
    comFicha()
    abrir()

    const lista = await screen.findByRole('list', { name: 'Sessoes' })
    const faixa = screen.getByRole('complementary', { name: 'Pendencias' })
    expect(faixa.contains(lista)).toBe(false)
    expect(lista.closest('.colunas-ficha')).toBeNull()
  })

  it('paciente sem sessao nenhuma diz o que fazer', async () => {
    comFicha({ ...FICHA, linhaDoTempo: [], pendencias: [] })
    abrir()

    expect(await screen.findByText(/nenhuma sessao registrada/i)).toBeInTheDocument()
  })

  // I1 da revisao: `DialogoDePaciente` aceitava `paciente`, mandava o PUT e
  // desenhava o titulo "Editar paciente" — com teste verde — e NENHUMA tela
  // passava a prop. A feature existia e era inalcancavel. Os dois testes
  // abaixo cobram a porta (o botao abre o dialogo com o paciente da ficha) e a
  // volta (salvar grava e a propria ficha mostra o dado novo).
  it('o botao Editar abre o dialogo ja preenchido com o paciente da ficha', async () => {
    const usuario = userEvent.setup()
    comFicha()
    abrir()

    await usuario.click(await screen.findByRole('button', { name: 'Editar' }))

    expect(await screen.findByRole('heading', { name: 'Editar paciente' })).toBeInTheDocument()
    expect(screen.getByLabelText(/nome/i)).toHaveValue('Helena Vaz')
    expect(screen.getByLabelText(/telefone/i)).toHaveValue('51998112345')
  })

  it('salvar a edicao manda o PUT e a ficha passa a mostrar o dado novo', async () => {
    const NOVO = 'Helena Vaz Ribeiro'
    let corpoDoPut: Record<string, unknown> | null = null
    // a ficha responde o nome VELHO na primeira leitura e o NOVO nas
    // seguintes: e o que prova a invalidacao de `CHAVE_DA_FICHA` em
    // `useSalvarPaciente`. Sem ela o cabecalho seguiria com "Helena Vaz"
    // depois de um "Salvar" bem-sucedido — o defeito de segunda ordem que
    // editar A PARTIR DA FICHA trouxe junto.
    let leituras = 0
    servidor.use(
      http.get(`/pacientes/${ID}/ficha`, () => {
        leituras += 1
        return HttpResponse.json(
          leituras === 1 ? FICHA : { ...FICHA, paciente: { ...FICHA.paciente, nome: NOVO } },
        )
      }),
      http.put(`/pacientes/${ID}`, async ({ request }) => {
        corpoDoPut = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...FICHA.paciente, nome: NOVO })
      }),
    )
    const usuario = userEvent.setup()
    abrir()

    await usuario.click(await screen.findByRole('button', { name: 'Editar' }))
    const campoNome = await screen.findByLabelText(/nome/i)
    await usuario.clear(campoNome)
    await usuario.type(campoNome, NOVO)
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByRole('heading', { name: NOVO })).toBeInTheDocument()
    expect(corpoDoPut).toMatchObject({ nome: NOVO })
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
