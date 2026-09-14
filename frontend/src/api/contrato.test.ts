import { describe, expectTypeOf, it } from 'vitest'

import type { paths } from './contrato'

/**
 * Teste de TIPO, nao de runtime: ele falha na compilacao se o contrato
 * regenerado deixar de ter uma rota que esta fatia consome. E o alarme de
 * divergencia entre front e API — o motivo de o contrato existir.
 */
describe('contrato gerado', () => {
  it('tem as rotas que a Fatia 1 consome', () => {
    expectTypeOf<paths['/painel/hoje']['get']>().not.toBeNever()
    expectTypeOf<paths['/consultas/conflito']['get']>().not.toBeNever()
    expectTypeOf<paths['/consultas/{id}/status']['patch']>().not.toBeNever()
    expectTypeOf<paths['/auth/login']['post']>().not.toBeNever()
  })
})
