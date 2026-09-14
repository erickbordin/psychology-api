import { describe, expect, it } from 'vitest'

import { deApi, paraApi } from './data'

describe('data no formato que a API aceita', () => {
  it('formata sem offset e sem milissegundos', () => {
    // 2 de dezembro de 2026, 14h00 — mes em JS e base zero
    expect(paraApi(new Date(2026, 11, 2, 14, 0, 0))).toBe('2026-12-02T14:00:00')
  })

  it('preenche com zero a esquerda', () => {
    expect(paraApi(new Date(2026, 0, 5, 9, 7, 3))).toBe('2026-01-05T09:07:03')
  })

  it('NAO usa toISOString, que produziria Z e milissegundos', () => {
    const d = new Date(2026, 11, 2, 14, 0, 0)
    expect(paraApi(d)).not.toContain('Z')
    expect(paraApi(d)).not.toContain('.')
  })

  it('le de volta o que a API devolve, como hora local', () => {
    const d = deApi('2026-12-02T14:00:00')
    expect(d.getHours()).toBe(14)
    expect(d.getDate()).toBe(2)
  })
})
