import { screen, within } from '@testing-library/react'
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
  servidor.use(http.get('/ajustes', () => HttpResponse.json(corpo)))
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
    expect(screen.getByRole('region', { name: 'Aviso de consulta' })).toBeInTheDocument()
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

  it('salvar manda o PUT completo, com o expediente como veio, e anuncia o sucesso', async () => {
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

  it('valor vazio vai como nulo, expediente ausente continua ausente e o titulo guardado e mantido com a caixa desligada', async () => {
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

  it('erro de expediente, que nao esta na tela, aparece como alerta com a mensagem do servidor', async () => {
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

    expect(await screen.findByRole('alert')).toHaveTextContent('Dados inválidos.')
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
})
