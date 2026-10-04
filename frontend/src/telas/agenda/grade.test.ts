import { describe, expect, it } from 'vitest'

import { horasDaGrade } from './grade'

describe('horasDaGrade', () => {
  it('sem expediente mostra das 07h as 19h', () => {
    expect(horasDaGrade(null, null, [])).toEqual([7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19])
  })
  it('expediente de 8 a 18 vai ate a linha das 17h', () => {
    expect(horasDaGrade(8, 18, [])).toEqual([8, 9, 10, 11, 12, 13, 14, 15, 16, 17])
  })
  it('expediente ate 24 termina na linha das 23h', () => {
    expect(horasDaGrade(20, 24, [])).toEqual([20, 21, 22, 23])
  })
  it('sessao antes do inicio estica a grade para cima', () => {
    expect(horasDaGrade(8, 12, [6])).toEqual([6, 7, 8, 9, 10, 11])
  })
  it('sessao depois do fim estica a grade para baixo', () => {
    expect(horasDaGrade(8, 12, [20])).toEqual([8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20])
  })
  it('sessao dentro do expediente nao muda nada', () => {
    expect(horasDaGrade(8, 12, [9])).toEqual([8, 9, 10, 11])
  })
})
