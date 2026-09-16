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
// nunca veria a regra `[data-borrado='true'] { filter: blur(5px) }`, mesmo
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
    comPainel()
    renderizar(<HojePage />)

    const linhas = await screen.findAllByRole('listitem', { name: /sessao/i })
    expect(linhas).toHaveLength(2)
    expect(within(linhas[0]).getByText(/helena vaz/i)).toBeInTheDocument()
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

    const linhas = await screen.findAllByRole('listitem', { name: /sessao/i })
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

    const linhas = await screen.findAllByRole('listitem', { name: /sessao/i })
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

    const faixa = await screen.findByRole('region', { name: /pendencia/i })
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

    const faixa = await screen.findByRole('region', { name: /pendencia/i })
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

    const faixa = await screen.findByRole('region', { name: /pendencia/i })
    expect(within(faixa).getByText(/marque o que aconteceu/i)).toBeInTheDocument()
    expect(within(faixa).getByText(/registre a anotacao/i)).toBeInTheDocument()
  })

  it('modo privacidade borra os nomes com um clique', async () => {
    comPainel()
    const usuario = userEvent.setup()
    renderizar(<HojePage />)

    // Escopado ao destaque: Rui Sampaio aparece DUAS vezes na tela (no
    // destaque da proxima sessao e de novo na lista do dia, porque a proxima
    // tambem faz parte da lista). Um `findByText`/`getByText` sem escopo
    // encontra os dois nos e falha com "multiple elements" — o mesmo
    // problema que o brief ja evitou em "destaque"/"linhas[0]" com `within`.
    const destaque = await screen.findByRole('region', { name: /proxima sessao/i })

    // ANTES de clicar: sem privacidade, nada borra. Sem esta linha, um teste
    // que so checa o estado "ligado" nao prova que o clique MUDOU alguma
    // coisa de verdade.
    const antes = await within(destaque).findByText(/rui sampaio/i)
    expect(getComputedStyle(antes).filter).not.toBe('blur(5px)')

    await usuario.click(screen.getByRole('button', { name: /privacidade/i }))

    // reconsulta DEPOIS do clique (nao reusa `antes`): `Nome` e definido
    // dentro do corpo de `HojePage`, entao a re-renderizacao troca a
    // identidade do componente e o React desmonta/remonta o <span> — o no
    // antigo fica orfao no DOM antigo e `getComputedStyle` nele sempre
    // devolveria "none", mesmo com a regra aplicada no elemento novo.
    const depois = within(destaque).getByText(/rui sampaio/i)

    // a spec 4 pede BORRAR, nao remover: o psicologo continua sabendo onde
    // cada sessao esta, sem que quem entra na sala leia o nome. Prova o
    // EFEITO (o filtro que o navegador de fato calcula pra esse elemento),
    // nao so a marca — a marca sozinha (`data-borrado="true"`) e o que o
    // Critico 1 da revisao do conjunto achou passando com o borrao morto:
    // nenhuma regra CSS existia pro atributo em lugar nenhum do repositorio.
    expect(getComputedStyle(depois).filter).toBe('blur(5px)')
    // documenta a amarracao: e a regra `[data-borrado='true']` de
    // estilo/estilo.css que produz o filtro acima, entao continua valendo
    // conferir a marca tambem
    expect(depois).toHaveAttribute('data-borrado', 'true')
  })

  it('dia 1 do psicologo: diz o que fazer, e nao e erro', async () => {
    comPainel({ proxima: null, sessoes: [], pendencias: [] })
    renderizar(<HojePage />)

    expect(await screen.findByText(/nenhuma sessao hoje/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /agenda/i })).toBeInTheDocument()
  })
})
