import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderizar } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import { TermosPage } from './TermosPage'

const textos = {
  versao: '2026-10-01',
  termos: 'Primeiro paragrafo dos termos.\n\nSegundo paragrafo dos termos.',
  privacidade: 'Texto da privacidade.\n\nOutro paragrafo da privacidade.',
}

describe('termos e privacidade', () => {
  it('mostra o titulo, a versao e os paragrafos dos termos', async () => {
    servidor.use(http.get('/termos/atual', () => HttpResponse.json(textos)))
    renderizar(<TermosPage documento="termos" />)

    expect(screen.getByRole('heading', { name: 'Termos de Uso' })).toBeInTheDocument()
    expect(screen.getByText('Carregando…')).toBeInTheDocument()
    expect(await screen.findByText('Versão 2026-10-01')).toBeInTheDocument()
    expect(screen.getByText('Primeiro paragrafo dos termos.')).toBeInTheDocument()
    expect(screen.getByText('Segundo paragrafo dos termos.')).toBeInTheDocument()
    expect(screen.queryByText(/privacidade/i)).not.toBeInTheDocument()
  })

  it('mostra o texto da politica de privacidade', async () => {
    servidor.use(http.get('/termos/atual', () => HttpResponse.json(textos)))
    renderizar(<TermosPage documento="privacidade" />)

    expect(screen.getByRole('heading', { name: 'Política de Privacidade' })).toBeInTheDocument()
    expect(await screen.findByText('Texto da privacidade.')).toBeInTheDocument()
    expect(screen.getByText('Outro paragrafo da privacidade.')).toBeInTheDocument()
    expect(screen.getByText('Versão 2026-10-01')).toBeInTheDocument()
  })

  it('nao interpreta HTML do markdown: o script aparece como texto', async () => {
    servidor.use(
      http.get('/termos/atual', () =>
        HttpResponse.json({ ...textos, termos: '<script>alert(1)</script>\n\n<b>negrito</b>' }),
      ),
    )
    const { container } = renderizar(<TermosPage documento="termos" />)

    expect(await screen.findByText('<script>alert(1)</script>')).toBeInTheDocument()
    expect(screen.getByText('<b>negrito</b>')).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
  })

  it('diz que ainda nao foi publicado quando a API responde 404', async () => {
    servidor.use(
      http.get('/termos/atual', () =>
        HttpResponse.json({ status: 404, mensagem: 'Não encontrado.', erros: [] }, { status: 404 }),
      ),
    )
    renderizar(<TermosPage documento="termos" />)

    expect(await screen.findByText('Ainda não publicado.')).toBeInTheDocument()
  })

  it('avisa quando o carregamento falha por outro motivo', async () => {
    servidor.use(
      http.get('/termos/atual', () =>
        HttpResponse.json({ status: 500, mensagem: 'Falhou.', erros: [] }, { status: 500 }),
      ),
    )
    renderizar(<TermosPage documento="termos" />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar. Tente de novo em instantes.',
    )
  })
})
