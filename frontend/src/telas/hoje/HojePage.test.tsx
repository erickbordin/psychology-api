import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { HojePage } from './HojePage'

// Critico 1 da revisao do conjunto: o modo privacidade marcava
// `data-borrado="true"` no nome, e NENHUMA regra CSS existia pra esse
// atributo em lugar nenhum do repositorio — o teste antigo provava so a
// marca (`toHaveAttribute`), nunca o efeito, e passava com o borrao morto.
// Testes de tela nao passam por `main.tsx` (onde o app "de verdade" carrega
// a folha de estilo global) — sem este import, `getComputedStyle` abaixo
// nunca veria a regra `[data-borrado='true'] { filter: blur(6px) saturate(0.7) }`, mesmo
// que ela exista em estilo/estilo.css e o app real a aplique. Vitest com
// `css: true` (vite.config.ts) processa este import e injeta a folha real no
// `document` do jsdom — confirmado empiricamente que o jsdom desta versao
// (29.x) CALCULA `getComputedStyle(...).filter` a partir dela.
import '../../estilo/estilo.css'

const PAINEL = {
  proxima: {
    consultaId: '11111111-1111-1111-1111-111111111111',
    dtConsulta: '2026-12-02T15:00:00',
    pacienteId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    pacienteNome: 'Rui Sampaio',
    status: 'AGENDADA',
  },
  sessoes: [
    {
      consultaId: '22222222-2222-2222-2222-222222222222',
      dtConsulta: '2026-12-02T08:00:00',
      pacienteId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      pacienteNome: 'Helena Vaz',
      status: 'AGENDADA',
    },
    {
      consultaId: '11111111-1111-1111-1111-111111111111',
      dtConsulta: '2026-12-02T15:00:00',
      pacienteId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      pacienteNome: 'Rui Sampaio',
      status: 'AGENDADA',
    },
  ],
  pendencias: [
    {
      consultaId: '22222222-2222-2222-2222-222222222222',
      dtConsulta: '2026-12-02T08:00:00',
      pacienteNome: 'Helena Vaz',
      tipo: 'SEM_STATUS',
    },
  ],
}

// `Record<string, unknown>`, nao `unknown`: `HttpResponse.json` exige
// `JsonBodyType` (basicamente um objeto), e `unknown` cru nao satisfaz isso
// sem cast — e cast e o que este plano ja proibiu.
const comPainel = (painel: Record<string, unknown> = PAINEL) =>
  servidor.use(http.get('/painel/hoje', () => HttpResponse.json(painel)))

describe('tela Hoje', () => {
  it('mostra a proxima sessao em destaque', async () => {
    comPainel()
    renderizar(<HojePage />)

    const destaque = await screen.findByRole('region', { name: /proxima sessao/i })
    expect(within(destaque).getByText(/rui sampaio/i)).toBeInTheDocument()
  })

  it('lista o dia em ordem, com o status de cada sessao', async () => {
    comPainel({
      proxima: null,
      sessoes: [
        {
          consultaId: '44444444-4444-4444-4444-444444444444',
          dtConsulta: '2026-12-02T09:00:00',
          pacienteId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          pacienteNome: 'Ana Lopes',
          status: 'REALIZADA',
        },
        {
          consultaId: '55555555-5555-5555-5555-555555555555',
          dtConsulta: '2026-12-02T10:00:00',
          pacienteId: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
          pacienteNome: 'Bruno Reis',
          status: 'FALTOU',
        },
      ],
      pendencias: [],
    })
    renderizar(<HojePage />)

    const lista = await screen.findByRole('region', { name: /sessoes de hoje/i })
    const linhas = within(lista).getAllByRole('listitem', { name: /sessao/i })
    expect(linhas).toHaveLength(2)
    expect(within(linhas[0]).getByText(/ana lopes/i)).toBeInTheDocument()
    expect(within(linhas[0]).getByText('REALIZADA')).toBeInTheDocument()
    expect(within(linhas[1]).getByText(/bruno reis/i)).toBeInTheDocument()
    expect(within(linhas[1]).getByText('FALTOU')).toBeInTheDocument()
  })

  it('marca Realizada em um clique na propria linha, sem abrir dialogo', async () => {
    comPainel()
    let corpoEnviado: unknown
    servidor.use(
      http.patch('/consultas/:id/status', async ({ request }) => {
        corpoEnviado = await request.json()
        return HttpResponse.json({ status: 'REALIZADA' })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    const linhas = within(fila).getAllByRole('listitem', { name: /sessao/i })
    await usuario.click(within(linhas[0]).getByRole('button', { name: /realizada/i }))

    expect(corpoEnviado).toEqual({ status: 'REALIZADA' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('marcar Realizada falha (409) e a psicologa ve o motivo, sem morrer em silencio', async () => {
    // Critico 3 da revisao do conjunto: a API recusa a rota com 409 quando a
    // sessao ja esta CANCELADA — a frase e a mesma que o backend devolve
    // (`ConsultaService.java:232`). Antes deste conserto a tela nao mostrava
    // nada e o chip continuava "AGENDADA" — o mesmo D11 do dialogo, so que
    // sem o conserto.
    comPainel()
    const MENSAGEM = 'Esta sessão foi cancelada. Reagende-a para poder marcar presença.'
    servidor.use(
      http.patch('/consultas/:id/status', () =>
        HttpResponse.json({ status: 409, mensagem: MENSAGEM, erros: [] }, { status: 409 }),
      ),
    )
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    const linhas = within(fila).getAllByRole('listitem', { name: /sessao/i })
    await usuario.click(within(linhas[0]).getByRole('button', { name: /realizada/i }))

    // texto primeiro, role depois: o mesmo cuidado do
    // `DialogoDeConsulta.test.tsx` — um `findByRole('alert')` bruto pode achar
    // mais de um elemento se outra regiao viva existir na tela
    const aviso = await within(linhas[0]).findByText(MENSAGEM)
    expect(aviso).toHaveAttribute('role', 'alert')

    // a API recusou: nao ha estado otimista, o chip continua o que era
    expect(within(linhas[0]).getByText('AGENDADA')).toBeInTheDocument()
  })

  it('mostra quanto falta para a proxima, calculado no cliente', async () => {
    // So `Date` e falso aqui, nao os timers inteiros: `vi.useFakeTimers()` sem
    // escopo tambem congela o `setTimeout` que o MSW e o `waitFor` usam por
    // baixo, e o `findByRole` abaixo nunca resolve — o teste trava nos 5s do
    // timeout global, e o `vi.useRealTimers()' no fim nunca roda, vazando o
    // relogio falso para os testes seguintes (foi o que aconteceu na primeira
    // tentativa: 4 testes deste arquivo travaram em cadeia).
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 11, 2, 14, 30, 0)) // 30 min antes das 15h
    comPainel()
    renderizar(<HojePage />)

    const destaque = await screen.findByRole('region', { name: /proxima sessao/i })
    expect(within(destaque).getByText(/em 30 min/i)).toBeInTheDocument()
    vi.useRealTimers()
  })

  it('mostra a faixa de pendencias', async () => {
    comPainel()
    renderizar(<HojePage />)

    const faixa = await screen.findByRole('region', { name: /precisa de voce/i })
    expect(within(faixa).getByText(/helena vaz/i)).toBeInTheDocument()
  })

  it('nao rotula a pendencia como "sem status" — ha duas especies na fila', async () => {
    // A Fatia 2 do backend passou a mandar tambem `SEM_ANOTACAO`: sessao JA
    // realizada, sem anotacao escrita. A tela escrevia "· sem status" fixo em
    // toda linha, e rotulava aquela como se ninguem tivesse dito o que houve.
    //
    // O fixture abaixo e uma pendencia da especie nova, exatamente como o
    // backend a devolve. Se alguem repuser a frase fixa, este teste fica
    // vermelho — que e o unico jeito de a correcao se sustentar.
    comPainel({
      proxima: null,
      sessoes: [],
      pendencias: [
        {
          consultaId: '33333333-3333-3333-3333-333333333333',
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Teodoro Alencar',
          tipo: 'SEM_ANOTACAO',
        },
      ],
    })
    renderizar(<HojePage />)

    const faixa = await screen.findByRole('region', { name: /precisa de voce/i })
    expect(within(faixa).getByText(/teodoro alencar/i)).toBeInTheDocument()
    expect(within(faixa).queryByText(/sem status/i)).not.toBeInTheDocument()
  })

  it('escreve a frase certa para cada especie de pendencia', async () => {
    comPainel({
      proxima: null,
      sessoes: [],
      pendencias: [
        {
          consultaId: '22222222-2222-2222-2222-222222222222',
          dtConsulta: '2026-12-02T08:00:00',
          pacienteNome: 'Helena Vaz',
          tipo: 'SEM_STATUS',
        },
        {
          consultaId: '33333333-3333-3333-3333-333333333333',
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Teodoro Alencar',
          tipo: 'SEM_ANOTACAO',
        },
      ],
    })
    renderizar(<HojePage />)

    const faixa = await screen.findByRole('region', { name: /precisa de voce/i })
    expect(within(faixa).getByText(/marque o que aconteceu/i)).toBeInTheDocument()
    expect(within(faixa).getByText(/registre a anotacao/i)).toBeInTheDocument()
  })

  it('sem privacidade, o nome nao borra', async () => {
    comPainel()
    renderizar(<HojePage />)

    // Escopado ao destaque: Rui Sampaio aparece DUAS vezes na tela (no
    // destaque da proxima sessao e de novo na fila "Precisa de voce", porque
    // a proxima tambem faz parte da fila). Um `findByText`/`getByText` sem escopo
    // encontra os dois nos e falha com "multiple elements" — o mesmo
    // problema que o brief ja evitou em "destaque"/"linhas[0]" com `within`.
    const destaque = await screen.findByRole('region', { name: /proxima sessao/i })
    const nome = await within(destaque).findByText(/rui sampaio/i)
    expect(getComputedStyle(nome).filter).not.toBe('blur(6px) saturate(0.7)')
  })

  it('com privacidade ligada, o nome borra', async () => {
    comPainel()
    renderizar(<HojePage />, { privacidadeLigada: true })

    const destaque = await screen.findByRole('region', { name: /proxima sessao/i })
    const nome = await within(destaque).findByText(/rui sampaio/i)

    // a spec 4 pede BORRAR, nao remover: o psicologo continua sabendo onde
    // cada sessao esta, sem que quem entra na sala leia o nome. Prova o
    // EFEITO (o filtro que o navegador de fato calcula pra esse elemento),
    // nao so a marca — a marca sozinha (`data-borrado="true"`) e o que o
    // Critico 1 da revisao do conjunto achou passando com o borrao morto:
    // nenhuma regra CSS existia pro atributo em lugar nenhum do repositorio.
    expect(getComputedStyle(nome).filter).toBe('blur(6px) saturate(0.7)')
    // documenta a amarracao: e a regra `[data-borrado='true']` de
    // estilo/estilo.css que produz o filtro acima, entao continua valendo
    // conferir a marca tambem
    expect(nome).toHaveAttribute('data-borrado', 'true')
  })

  it('a fila junta pendencia e sessao agendada, ordenadas por hora', async () => {
    comPainel()
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    const linhas = within(fila).getAllByRole('listitem')
    expect(linhas).toHaveLength(2)
    expect(within(linhas[0]).getByText(/helena vaz/i)).toBeInTheDocument()
    expect(within(linhas[1]).getByText(/rui sampaio/i)).toBeInTheDocument()
  })

  it('a sessao ja resolvida nao fica na fila', async () => {
    comPainel({
      proxima: null,
      sessoes: [
        {
          consultaId: '44444444-4444-4444-4444-444444444444',
          dtConsulta: '2026-12-02T09:00:00',
          pacienteId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          pacienteNome: 'Ana Lopes',
          status: 'REALIZADA',
        },
        {
          consultaId: '11111111-1111-1111-1111-111111111111',
          dtConsulta: '2026-12-02T15:00:00',
          pacienteId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
          pacienteNome: 'Rui Sampaio',
          status: 'AGENDADA',
        },
      ],
      pendencias: [],
    })
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    expect(within(fila).getByText(/rui sampaio/i)).toBeInTheDocument()
    expect(within(fila).queryByText(/ana lopes/i)).toBeNull()

    const resolvidas = screen.getByRole('region', { name: /sessoes de hoje/i })
    expect(within(resolvidas).getByText(/ana lopes/i)).toBeInTheDocument()
  })

  it('a linha marcavel continua marcavel a partir da fila', async () => {
    comPainel()
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    expect(within(fila).getAllByRole('button', { name: /realizada/i }).length).toBeGreaterThan(0)
  })

  it('dia 1 do psicologo: diz o que fazer, e nao e erro', async () => {
    comPainel({ proxima: null, sessoes: [], pendencias: [] })
    renderizar(<HojePage />)

    expect(await screen.findByText(/nenhuma sessao hoje/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /agenda/i })).toBeInTheDocument()
  })

  it('pendencia SEM_BAIXA mostra a frase e o botao Dar baixa, que faz PATCH na baixa', async () => {
    const ID = '66666666-6666-6666-6666-666666666666'
    comPainel({
      proxima: null,
      sessoes: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          pacienteNome: 'Ana Lopes',
          status: 'REALIZADA',
          valor: 180,
          pagoEm: null,
        },
      ],
      pendencias: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Ana Lopes',
          tipo: 'SEM_BAIXA',
        },
      ],
    })
    let metodo = ''
    let caminho = ''
    servidor.use(
      http.patch('/consultas/:id/baixa', ({ request }) => {
        metodo = request.method
        caminho = new URL(request.url).pathname
        return HttpResponse.json({ id: ID, status: 'REALIZADA' })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    expect(within(fila).getByText(/dê baixa no pagamento/)).toBeInTheDocument()
    await usuario.click(within(fila).getByRole('button', { name: 'Dar baixa' }))

    await vi.waitFor(() => expect(caminho).toBe(`/consultas/${ID}/baixa`))
    expect(metodo).toBe('PATCH')
  })

  it('duas pendencias da mesma sessao aparecem em UMA linha, juntas', async () => {
    const ID = '77777777-7777-7777-7777-777777777777'
    comPainel({
      proxima: null,
      sessoes: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          pacienteNome: 'Ana Lopes',
          status: 'REALIZADA',
          valor: 180,
          pagoEm: null,
        },
      ],
      pendencias: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Ana Lopes',
          tipo: 'SEM_ANOTACAO',
        },
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Ana Lopes',
          tipo: 'SEM_BAIXA',
        },
      ],
    })
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    const linhas = within(fila).getAllByRole('listitem', { name: /sessao/i })
    expect(linhas).toHaveLength(1)
    expect(
      within(linhas[0]).getByText(/registre a anotacao · dê baixa no pagamento/),
    ).toBeInTheDocument()
  })

  it('sessao realizada e paga mostra Pago e Desfazer baixa, que faz DELETE na baixa', async () => {
    const ID = '88888888-8888-8888-8888-888888888888'
    comPainel({
      proxima: null,
      sessoes: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          pacienteNome: 'Ana Lopes',
          status: 'REALIZADA',
          valor: 180,
          pagoEm: '2026-12-02T10:00:00',
        },
      ],
      pendencias: [],
    })
    let metodo = ''
    let caminho = ''
    servidor.use(
      http.delete('/consultas/:id/baixa', ({ request }) => {
        metodo = request.method
        caminho = new URL(request.url).pathname
        return HttpResponse.json({ id: ID, status: 'REALIZADA' })
      }),
    )
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    const resolvidas = await screen.findByRole('region', { name: /sessoes de hoje/i })
    const linha = within(resolvidas).getByRole('listitem', { name: /sessao/i })
    expect(within(linha).getByText('Pago')).toBeInTheDocument()
    await usuario.click(within(linha).getByRole('button', { name: 'Desfazer baixa' }))

    await vi.waitFor(() => expect(caminho).toBe(`/consultas/${ID}/baixa`))
    expect(metodo).toBe('DELETE')
  })

  it('Dar baixa recusado (409) mostra o motivo dentro da linha', async () => {
    const ID = '99999999-9999-9999-9999-999999999999'
    const MENSAGEM = 'Esta sessão já foi baixada.'
    comPainel({
      proxima: null,
      sessoes: [],
      pendencias: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Ana Lopes',
          tipo: 'SEM_BAIXA',
        },
      ],
    })
    servidor.use(
      http.patch('/consultas/:id/baixa', () =>
        HttpResponse.json({ status: 409, mensagem: MENSAGEM, erros: [] }, { status: 409 }),
      ),
    )
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    const fila = await screen.findByRole('region', { name: /precisa de voce/i })
    const linha = within(fila).getByRole('listitem', { name: /sessao/i })
    await usuario.click(within(linha).getByRole('button', { name: 'Dar baixa' }))

    const aviso = await within(linha).findByText(MENSAGEM)
    expect(aviso).toHaveAttribute('role', 'alert')
  })

  it('a tela nao mostra valor em reais', async () => {
    const ID = 'aaaaaaaa-1111-2222-3333-444444444444'
    comPainel({
      proxima: null,
      sessoes: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          pacienteNome: 'Ana Lopes',
          status: 'REALIZADA',
          valor: 180,
          pagoEm: null,
        },
      ],
      pendencias: [
        {
          consultaId: ID,
          dtConsulta: '2026-12-02T09:00:00',
          pacienteNome: 'Ana Lopes',
          tipo: 'SEM_BAIXA',
        },
      ],
    })
    renderizar(<HojePage />)

    await screen.findByRole('region', { name: /precisa de voce/i })
    expect(screen.queryByText(/R\$/)).toBeNull()
  })
})
