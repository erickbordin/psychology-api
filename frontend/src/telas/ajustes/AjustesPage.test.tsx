import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { AjustesPage } from './AjustesPage'

import '../../estilo/estilo.css'

const AJUSTES = {
  nome: 'Ana Souza',
  email: 'ana@exemplo.local',
  valorPadraoSessao: 180,
  antecedencia: 'H24',
  blocosAviso: ['REMARCAR'],
  tituloNoEmail: false,
  tituloProfissional: null,
  resumoDiario: true,
  expedienteInicio: 8,
  expedienteFim: 18,
}

const erro = (status: number, mensagem: string, erros: { campo: string; erro: string }[] = []) =>
  HttpResponse.json({ status, mensagem, erros }, { status })

function servirGet(corpo: object = AJUSTES) {
  servidor.use(
    http.get('/ajustes', () => HttpResponse.json(corpo)),
    http.get('/auditoria', () =>
      HttpResponse.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } }),
    ),
  )
}

const previa = () => screen.getByRole('region', { name: 'Como o aviso chega' })

async function montar(corpo: object = AJUSTES) {
  servirGet(corpo)
  renderizar(<AjustesPage />)
  await screen.findByRole('heading', { name: 'Ajustes' })
  await screen.findByDisplayValue('Ana Souza')
}

afterEach(() => {
  vi.useRealTimers()
})

describe('AjustesPage', () => {
  it('carrega e preenche o formulario a partir do GET', async () => {
    await montar()

    expect(screen.getByRole('region', { name: 'Perfil' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Sessão' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Expediente' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Aviso de consulta' })).toBeInTheDocument()
    expect(screen.getByLabelText('Início')).toHaveValue('8')
    expect(screen.getByLabelText('Fim')).toHaveValue('18')
    expect(
      screen.getByText('A agenda mostra só estas horas. Sem expediente, mostra das 07h às 19h.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/^Nome/)).toHaveValue('Ana Souza')
    expect(screen.getByLabelText('E-mail')).toHaveValue('ana@exemplo.local')
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('readonly')
    expect(screen.getByLabelText(/^Valor padrão da sessão/)).toHaveValue(180)
    expect(screen.getByLabelText('Antecedência')).toHaveValue('H24')
    expect(screen.getByLabelText('Se precisar remarcar, é só responder este e-mail.')).toBeChecked()
    expect(screen.getByLabelText('Chegue com alguns minutos de antecedência.')).not.toBeChecked()
    expect(screen.getByLabelText('Receber às 18h o resumo da agenda de amanhã')).toBeChecked()
    expect(screen.getByText('Usado quando o paciente não tem valor próprio.')).toBeInTheDocument()
    expect(screen.getByText('Estas duas frases sempre aparecem.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salvar ajustes' })).toBeEnabled()
  })

  it('salvar manda o PUT completo, com o expediente do formulario, e anuncia o sucesso', async () => {
    const usuario = userEvent.setup()
    let corpo: unknown = null
    servidor.use(
      http.put('/ajustes', async ({ request }) => {
        corpo = await request.json()
        return HttpResponse.json({ ...AJUSTES, nome: 'Ana Lima' })
      }),
    )
    await montar()

    await usuario.clear(screen.getByLabelText(/^Nome/))
    await usuario.type(screen.getByLabelText(/^Nome/), 'Ana Lima')
    await usuario.selectOptions(screen.getByLabelText('Antecedência'), 'VESPERA')
    await usuario.click(screen.getByLabelText('Nosso encontro dura 50 minutos.'))
    await usuario.click(screen.getByLabelText('Incluir meu título profissional no e-mail'))
    await usuario.type(screen.getByLabelText('Título profissional'), 'Psicóloga')
    await usuario.click(screen.getByLabelText('Receber às 18h o resumo da agenda de amanhã'))
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Ajustes salvos.')
    expect(corpo).toEqual({
      nome: 'Ana Lima',
      valorPadraoSessao: 180,
      antecedencia: 'VESPERA',
      blocosAviso: ['REMARCAR', 'DURACAO'],
      tituloNoEmail: true,
      tituloProfissional: 'Psicóloga',
      resumoDiario: false,
      expedienteInicio: 8,
      expedienteFim: 18,
    })
  })

  it('valor vazio vai como nulo, expediente em branco vai nulo e o titulo guardado e mantido com a caixa desligada', async () => {
    const usuario = userEvent.setup()
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.put('/ajustes', async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(AJUSTES)
      }),
    )
    await montar({ ...AJUSTES, expedienteInicio: null, expedienteFim: null, tituloProfissional: 'Psicóloga' })

    await usuario.clear(screen.getByLabelText(/^Valor padrão da sessão/))
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    await screen.findByRole('status')
    expect(corpo.valorPadraoSessao).toBeNull()
    expect(corpo.expedienteInicio).toBeNull()
    expect(corpo.expedienteFim).toBeNull()
    expect(corpo.tituloNoEmail).toBe(false)
    expect(corpo.tituloProfissional).toBe('Psicóloga')
  })

  it('erros de campo do servidor pousam no campo', async () => {
    const usuario = userEvent.setup()
    servidor.use(
      http.put('/ajustes', () =>
        erro(400, 'Dados inválidos.', [
          { campo: 'nome', erro: 'O nome deve ter entre 2 e 100 caracteres.' },
          { campo: 'valorPadraoSessao', erro: 'O valor da sessão deve ser maior que zero.' },
        ]),
      ),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    const nome = screen.getByLabelText(/^Nome/)
    const valor = screen.getByLabelText(/^Valor padrão da sessão/)
    expect(await screen.findByText('O nome deve ter entre 2 e 100 caracteres.')).toBeInTheDocument()
    expect(nome).toHaveAccessibleDescription('O nome deve ter entre 2 e 100 caracteres.')
    expect(valor).toHaveAccessibleDescription('O valor da sessão deve ser maior que zero.')
    expect(screen.queryByText('Ajustes salvos.')).not.toBeInTheDocument()
  })

  it('erro do servidor no titulo e na antecedencia pousa no campo', async () => {
    const usuario = userEvent.setup()
    servidor.use(
      http.put('/ajustes', () =>
        erro(400, 'Dados inválidos.', [
          { campo: 'tituloProfissional', erro: 'Informe o título profissional para incluí-lo no e-mail.' },
          { campo: 'antecedencia', erro: 'Escolha a antecedência do aviso.' },
        ]),
      ),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByLabelText('Título profissional')).toHaveAccessibleDescription(
      'Informe o título profissional para incluí-lo no e-mail.',
    )
    expect(screen.getByLabelText('Antecedência')).toHaveAccessibleDescription('Escolha a antecedência do aviso.')
  })

  it('erro do servidor em expedienteInicio pousa no campo Início e nao vira alerta', async () => {
    const usuario = userEvent.setup()
    servidor.use(
      http.put('/ajustes', () =>
        erro(400, 'Dados inválidos.', [
          { campo: 'expedienteInicio', erro: 'Informe o início e o fim do expediente, ou nenhum dos dois.' },
        ]),
      ),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByLabelText('Início')).toHaveAccessibleDescription(
      'Informe o início e o fim do expediente, ou nenhum dos dois.',
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('erro do servidor em expedienteFim pousa no campo Fim e nao vira alerta', async () => {
    const usuario = userEvent.setup()
    servidor.use(
      http.put('/ajustes', () =>
        erro(400, 'Dados inválidos.', [
          { campo: 'expedienteFim', erro: 'O fim do expediente deve ser depois do início.' },
        ]),
      ),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByLabelText('Fim')).toHaveAccessibleDescription(
      'O fim do expediente deve ser depois do início.',
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('erro de campo desconhecido continua virando alerta', async () => {
    const usuario = userEvent.setup()
    servidor.use(
      http.put('/ajustes', () =>
        erro(400, 'Dados inválidos.', [{ campo: 'campoQueNaoExiste', erro: 'Valor inválido.' }]),
      ),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Dados inválidos.')
  })

  it('os selects de expediente oferecem Sem expediente e as horas cheias', async () => {
    await montar()

    const inicio = within(screen.getByLabelText('Início')).getAllByRole('option')
    const fim = within(screen.getByLabelText('Fim')).getAllByRole('option')
    expect(inicio).toHaveLength(25)
    expect(fim).toHaveLength(25)
    expect(inicio[0]).toHaveTextContent('Sem expediente')
    expect(inicio[1]).toHaveTextContent('00:00')
    expect(inicio[24]).toHaveTextContent('23:00')
    expect(fim[0]).toHaveTextContent('Sem expediente')
    expect(fim[1]).toHaveTextContent('01:00')
    expect(fim[24]).toHaveTextContent('24:00')
  })

  it('mudar o expediente para 08:00 a 18:00 e salvar manda os numeros', async () => {
    const usuario = userEvent.setup()
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.put('/ajustes', async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...AJUSTES, expedienteInicio: 8, expedienteFim: 18 })
      }),
    )
    await montar({ ...AJUSTES, expedienteInicio: 9, expedienteFim: 12 })

    await usuario.selectOptions(screen.getByLabelText('Início'), '08:00')
    await usuario.selectOptions(screen.getByLabelText('Fim'), '18:00')
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    await screen.findByText('Ajustes salvos.')
    expect(corpo.expedienteInicio).toBe(8)
    expect(corpo.expedienteFim).toBe(18)
  })

  it('Sem expediente nos dois manda nulo nos dois', async () => {
    const usuario = userEvent.setup()
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.put('/ajustes', async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...AJUSTES, expedienteInicio: null, expedienteFim: null })
      }),
    )
    await montar()

    await usuario.selectOptions(screen.getByLabelText('Início'), 'Sem expediente')
    await usuario.selectOptions(screen.getByLabelText('Fim'), 'Sem expediente')
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    await screen.findByText('Ajustes salvos.')
    expect(corpo.expedienteInicio).toBeNull()
    expect(corpo.expedienteFim).toBeNull()
    expect(screen.getByLabelText('Início')).toHaveValue('')
    expect(screen.getByLabelText('Fim')).toHaveValue('')
  })

  it('so o inicio preenchido mostra a mensagem no Fim, sem enviar', async () => {
    const usuario = userEvent.setup()
    let puts = 0
    servidor.use(
      http.put('/ajustes', () => {
        puts += 1
        return HttpResponse.json(AJUSTES)
      }),
    )
    await montar({ ...AJUSTES, expedienteInicio: null, expedienteFim: null })

    await usuario.selectOptions(screen.getByLabelText('Início'), '08:00')
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByLabelText('Fim')).toHaveAccessibleDescription(
      'Informe o início e o fim do expediente, ou nenhum dos dois.',
    )
    expect(puts).toBe(0)
  })

  it('so o fim preenchido mostra a mensagem no Início, sem enviar', async () => {
    const usuario = userEvent.setup()
    await montar({ ...AJUSTES, expedienteInicio: null, expedienteFim: null })

    await usuario.selectOptions(screen.getByLabelText('Fim'), '18:00')
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByLabelText('Início')).toHaveAccessibleDescription(
      'Informe o início e o fim do expediente, ou nenhum dos dois.',
    )
  })

  it('fim igual ou antes do inicio mostra a mensagem no Fim, sem enviar', async () => {
    const usuario = userEvent.setup()
    let puts = 0
    servidor.use(
      http.put('/ajustes', () => {
        puts += 1
        return HttpResponse.json(AJUSTES)
      }),
    )
    await montar()

    await usuario.selectOptions(screen.getByLabelText('Início'), '10:00')
    await usuario.selectOptions(screen.getByLabelText('Fim'), '10:00')
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByLabelText('Fim')).toHaveAccessibleDescription(
      'O fim do expediente deve ser depois do início.',
    )
    expect(puts).toBe(0)
  })

  it('exige o titulo com a caixa ligada, no cliente, sem enviar', async () => {
    const usuario = userEvent.setup()
    let puts = 0
    servidor.use(
      http.put('/ajustes', () => {
        puts += 1
        return HttpResponse.json(AJUSTES)
      }),
    )
    await montar()

    await usuario.click(screen.getByLabelText('Incluir meu título profissional no e-mail'))
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByText('Informe o título profissional para incluí-lo no e-mail.')).toBeInTheDocument()
    expect(puts).toBe(0)
  })

  it('o titulo fica desabilitado com a caixa desmarcada', async () => {
    const usuario = userEvent.setup()
    await montar()

    expect(screen.getByLabelText('Título profissional')).toBeDisabled()
    await usuario.click(screen.getByLabelText('Incluir meu título profissional no e-mail'))
    expect(screen.getByLabelText('Título profissional')).toBeEnabled()
  })

  it('a previa muda ao marcar uma frase e ao ligar o titulo, sem salvar', async () => {
    const usuario = userEvent.setup()
    let puts = 0
    servidor.use(
      http.put('/ajustes', () => {
        puts += 1
        return HttpResponse.json(AJUSTES)
      }),
    )
    await montar({ ...AJUSTES, tituloProfissional: 'Psicóloga' })

    expect(previa()).toHaveTextContent('Olá, Ana.')
    expect(previa()).toHaveTextContent('às 14:00.')
    expect(previa()).toHaveTextContent('Se precisar remarcar, é só responder este e-mail.')
    expect(previa()).not.toHaveTextContent('Nosso encontro dura 50 minutos.')
    expect(within(previa()).getByText('Ana Souza', { selector: 'dd' })).toBeInTheDocument()

    await usuario.click(screen.getByLabelText('Nosso encontro dura 50 minutos.'))
    expect(previa()).toHaveTextContent('Nosso encontro dura 50 minutos.')

    await usuario.click(screen.getByLabelText('Incluir meu título profissional no e-mail'))
    expect(within(previa()).getByText('Ana Souza · Psicóloga', { selector: 'dd' })).toBeInTheDocument()

    await usuario.clear(screen.getByLabelText('Título profissional'))
    expect(within(previa()).getByText('Ana Souza', { selector: 'dd' })).toBeInTheDocument()

    await usuario.clear(screen.getByLabelText(/^Nome/))
    await usuario.type(screen.getByLabelText(/^Nome/), 'Bia')
    expect(within(previa()).getByText('Bia', { selector: 'dd' })).toBeInTheDocument()
    expect(puts).toBe(0)
  })

  it('o assunto da previa e sempre Lembrete de agendamento', async () => {
    const usuario = userEvent.setup()
    await montar()

    expect(within(previa()).getByText('Lembrete de agendamento', { selector: 'dd' })).toBeInTheDocument()
    await usuario.click(screen.getByLabelText('Chegue com alguns minutos de antecedência.'))
    await usuario.selectOptions(screen.getByLabelText('Antecedência'), 'H2')
    expect(within(previa()).getByText('Lembrete de agendamento', { selector: 'dd' })).toBeInTheDocument()
  })

  it('a previa usa a data de amanha as 14:00 e nao traz token de verdade', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 7, 10, 0))
    await montar()

    expect(previa()).toHaveTextContent('Você tem um horário marcado para quinta-feira, 8 de outubro, às 14:00.')
    expect(previa()).toHaveTextContent('https://exemplo.local/descadastro/exemplo')
  })

  it('um segundo salvamento que falha tira o Ajustes salvos', async () => {
    const usuario = userEvent.setup()
    let chamadas = 0
    servidor.use(
      http.put('/ajustes', () => {
        chamadas += 1
        return chamadas === 1 ? HttpResponse.json(AJUSTES) : erro(500, 'Erro inesperado.')
      }),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))
    expect(await screen.findByText('Ajustes salvos.')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Erro inesperado.')
    expect(screen.queryByText('Ajustes salvos.')).not.toBeInTheDocument()
  })

  it('depois de salvar o formulario mostra o que o servidor guardou', async () => {
    const usuario = userEvent.setup()
    servidor.use(http.put('/ajustes', () => HttpResponse.json({ ...AJUSTES, nome: 'Ana Lima' })))
    await montar()

    await usuario.clear(screen.getByLabelText(/^Nome/))
    await usuario.type(screen.getByLabelText(/^Nome/), '  Ana Lima  ')
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByText('Ajustes salvos.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Nome/)).toHaveValue('Ana Lima')
  })

  it('nome so com espacos mostra Informe seu nome.', async () => {
    const usuario = userEvent.setup()
    await montar()

    await usuario.clear(screen.getByLabelText(/^Nome/))
    await usuario.type(screen.getByLabelText(/^Nome/), '   ')
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    expect(await screen.findByText('Informe seu nome.')).toBeInTheDocument()
  })

  it('o titulo digitado segue no PUT mesmo com a caixa desmarcada depois', async () => {
    const usuario = userEvent.setup()
    let corpo: Record<string, unknown> = {}
    servidor.use(
      http.put('/ajustes', async ({ request }) => {
        corpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(AJUSTES)
      }),
    )
    await montar()

    await usuario.click(screen.getByLabelText('Incluir meu título profissional no e-mail'))
    await usuario.type(screen.getByLabelText('Título profissional'), 'Psicóloga')
    await usuario.click(screen.getByLabelText('Incluir meu título profissional no e-mail'))
    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))

    await screen.findByText('Ajustes salvos.')
    expect(corpo.tituloNoEmail).toBe(false)
    expect(corpo.tituloProfissional).toBe('Psicóloga')
  })

  it('mostra o carregando enquanto o GET viaja', async () => {
    servidor.use(http.get('/ajustes', () => new Promise(() => undefined)))
    renderizar(<AjustesPage />)

    expect(screen.getByRole('heading', { name: 'Ajustes' })).toBeInTheDocument()
    expect(screen.getByText('Carregando…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Salvar ajustes' })).not.toBeInTheDocument()
  })

  it('mostra o erro quando o GET falha', async () => {
    servidor.use(http.get('/ajustes', () => erro(500, 'Erro inesperado.')))
    renderizar(<AjustesPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar. Tente de novo em instantes.')
    expect(screen.queryByRole('button', { name: 'Salvar ajustes' })).not.toBeInTheDocument()
  })

  it('Trocar senha fica no Perfil depois do E-mail e abre o dialogo', async () => {
    const usuario = userEvent.setup()
    await montar()

    const perfil = screen.getByRole('region', { name: 'Perfil' })
    const botao = within(perfil).getByRole('button', { name: 'Trocar senha' })
    expect(botao).toHaveAttribute('type', 'button')
    expect(screen.getByLabelText('E-mail').compareDocumentPosition(botao)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )

    await usuario.click(botao)

    expect(await screen.findByRole('dialog', { name: 'Trocar senha' })).toBeInTheDocument()
  })

  it('fechar o dialogo com Cancelar devolve o foco ao botao Trocar senha', async () => {
    const usuario = userEvent.setup()
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Trocar senha' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Trocar senha' })
    await usuario.click(within(dialogo).getByRole('button', { name: 'Cancelar' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Trocar senha' })).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Trocar senha' })).toHaveFocus()
  })

  it('trocar a senha anuncia no status da pagina, sem enviar o PUT dos ajustes', async () => {
    const usuario = userEvent.setup()
    let puts = 0
    let corpo: unknown = null
    servidor.use(
      http.put('/ajustes', () => {
        puts += 1
        return HttpResponse.json(AJUSTES)
      }),
      http.post('/ajustes/senha', async ({ request }) => {
        corpo = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Trocar senha' }))
    await usuario.type(await screen.findByLabelText(/^Senha atual/), 'senha-antiga-1')
    await usuario.type(screen.getByLabelText(/^Nova senha/), 'senha-nova-muito-boa')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Senha trocada. As outras sessões foram encerradas.',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(corpo).toEqual({ senhaAtual: 'senha-antiga-1', novaSenha: 'senha-nova-muito-boa' })
    expect(puts).toBe(0)
  })

  it('o ultimo evento ganha no status: Ajustes salvos troca o aviso da senha e vice-versa', async () => {
    const usuario = userEvent.setup()
    servidor.use(
      http.put('/ajustes', () => HttpResponse.json(AJUSTES)),
      http.post('/ajustes/senha', () => new HttpResponse(null, { status: 204 })),
    )
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Trocar senha' }))
    await usuario.type(await screen.findByLabelText(/^Senha atual/), 'senha-antiga-1')
    await usuario.type(screen.getByLabelText(/^Nova senha/), 'senha-nova-muito-boa')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))
    await screen.findByText('Senha trocada. As outras sessões foram encerradas.')

    await usuario.click(screen.getByRole('button', { name: 'Salvar ajustes' }))
    expect(await screen.findByText('Ajustes salvos.')).toBeInTheDocument()
    expect(screen.queryByText('Senha trocada. As outras sessões foram encerradas.')).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Trocar senha' }))
    await usuario.type(await screen.findByLabelText(/^Senha atual/), 'senha-antiga-2')
    await usuario.type(screen.getByLabelText(/^Nova senha/), 'senha-nova-muito-boa-2')
    await usuario.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Senha trocada. As outras sessões foram encerradas.')).toBeInTheDocument()
    expect(screen.queryByText('Ajustes salvos.')).not.toBeInTheDocument()
  })
})
