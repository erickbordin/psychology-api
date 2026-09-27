import { describe, expect, it } from 'vitest'

import dialogoCss from './dialogo.css?raw'
import estiloCss from './estilo.css?raw'
import tokensCss from './tokens.css?raw'

const definidos = (css: string) =>
  new Set(Array.from(css.matchAll(/^\s*(--[\w-]+)\s*:/gm), (m) => m[1]))

const usados = (css: string) =>
  new Set(Array.from(css.matchAll(/var\(\s*(--[\w-]+)/g), (m) => m[1]))

const FOLHAS: Array<[string, string]> = [
  ['tokens.css', tokensCss],
  ['estilo.css', estiloCss],
  ['dialogo.css', dialogoCss],
]

describe('tokens', () => {
  it('nenhuma folha usa token que nao existe', () => {
    const tokens = definidos(tokensCss)
    const orfaos: string[] = []

    for (const [nome, css] of FOLHAS) {
      const locais = definidos(css)
      for (const t of usados(css)) {
        if (!tokens.has(t) && !locais.has(t)) orfaos.push(`${nome}: ${t}`)
      }
    }

    expect(orfaos).toEqual([])
  })

  it('declara o par claro e escuro', () => {
    expect(tokensCss).toContain('prefers-color-scheme: dark')
  })

  it('mantem o kill-switch de movimento reduzido', () => {
    expect(tokensCss).toContain('prefers-reduced-motion: reduce')
  })
})
