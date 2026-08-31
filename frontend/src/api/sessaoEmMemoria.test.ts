import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  gravarAcesso,
  lerAcesso,
  registrarPerdaDeSessao,
  reiniciarSessaoEmMemoria,
} from './sessaoEmMemoria'

describe('sessao em memoria', () => {
  beforeEach(() => reiniciarSessaoEmMemoria())

  it('comeca sem token', () => {
    expect(lerAcesso()).toBeNull()
  })

  it('guarda e devolve o token', () => {
    gravarAcesso('abc')
    expect(lerAcesso()).toBe('abc')
  })

  it('avisa quem registrou interesse quando a sessao e perdida', () => {
    const aviso = vi.fn()
    registrarPerdaDeSessao(aviso)

    gravarAcesso('abc')
    gravarAcesso(null)

    expect(aviso).toHaveBeenCalledTimes(1)
  })

  it('nao avisa ao gravar null sobre uma sessao que ja estava vazia', () => {
    const aviso = vi.fn()
    registrarPerdaDeSessao(aviso)

    gravarAcesso(null)

    expect(aviso).not.toHaveBeenCalled()
  })
})
