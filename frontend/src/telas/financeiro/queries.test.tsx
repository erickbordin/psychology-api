import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { clienteDeTeste } from '../../teste/renderizar'
import { servidor } from '../../teste/servidor'
import {
  useAlterarValor,
  useDarBaixa,
  useDarBaixaEmLote,
  useDesfazerBaixa,
  useFinanceiroDoMes,
} from './queries'

const CONSULTA_A = '3f6c1b0e-5d2a-4c8e-9a41-7b2d0e6f1a11'
const CONSULTA_B = '9b8e2d47-1c3f-4a6b-8d05-2e7a4c9f3b22'

function montar() {
  const cliente = clienteDeTeste()
  const invalidar = vi.spyOn(cliente, 'invalidateQueries')
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={cliente}>{children}</QueryClientProvider>
  )
  return { invalidar, wrapper }
}

function esperarInvalidacaoDoDinheiro(invalidar: ReturnType<typeof vi.spyOn>) {
  expect(invalidar).toHaveBeenCalledWith({ queryKey: ['financeiro'] })
  expect(invalidar).toHaveBeenCalledWith({ queryKey: ['painel'] })
  expect(invalidar).toHaveBeenCalledWith({ queryKey: ['ficha'] })
}

describe('queries do financeiro', () => {
  it('dar baixa faz PATCH sem corpo e derruba financeiro, painel e ficha', async () => {
    const chamadas: { metodo: string; corpo: string }[] = []
    servidor.use(
      http.patch(`/consultas/${CONSULTA_A}/baixa`, async ({ request }) => {
        chamadas.push({ metodo: request.method, corpo: await request.text() })
        return HttpResponse.json({ id: CONSULTA_A, status: 'REALIZADA' })
      }),
    )
    const { invalidar, wrapper } = montar()
    const { result } = renderHook(() => useDarBaixa(), { wrapper })

    await result.current.mutateAsync(CONSULTA_A)

    expect(chamadas).toEqual([{ metodo: 'PATCH', corpo: '' }])
    esperarInvalidacaoDoDinheiro(invalidar)
  })

  it('desfazer baixa usa DELETE na mesma rota e derruba o dinheiro', async () => {
    const metodos: string[] = []
    servidor.use(
      http.delete(`/consultas/${CONSULTA_A}/baixa`, ({ request }) => {
        metodos.push(request.method)
        return HttpResponse.json({ id: CONSULTA_A, status: 'REALIZADA' })
      }),
    )
    const { invalidar, wrapper } = montar()
    const { result } = renderHook(() => useDesfazerBaixa(), { wrapper })

    await result.current.mutateAsync(CONSULTA_A)

    expect(metodos).toEqual(['DELETE'])
    esperarInvalidacaoDoDinheiro(invalidar)
  })

  it('baixa em lote manda os ids no corpo e devolve a contagem', async () => {
    const corpos: string[] = []
    servidor.use(
      http.post('/consultas/baixas', async ({ request }) => {
        corpos.push(await request.text())
        return HttpResponse.json({ consultasBaixadas: 2 })
      }),
    )
    const { invalidar, wrapper } = montar()
    const { result } = renderHook(() => useDarBaixaEmLote(), { wrapper })

    const resposta = await result.current.mutateAsync([CONSULTA_A, CONSULTA_B])

    expect(corpos).toEqual([JSON.stringify({ consultaIds: [CONSULTA_A, CONSULTA_B] })])
    expect(resposta).toEqual({ consultasBaixadas: 2 })
    esperarInvalidacaoDoDinheiro(invalidar)
  })

  it('alterar valor manda o valor no corpo e derruba o dinheiro', async () => {
    const corpos: string[] = []
    servidor.use(
      http.patch(`/consultas/${CONSULTA_A}/valor`, async ({ request }) => {
        corpos.push(await request.text())
        return HttpResponse.json({ id: CONSULTA_A, status: 'REALIZADA' })
      }),
    )
    const { invalidar, wrapper } = montar()
    const { result } = renderHook(() => useAlterarValor(), { wrapper })

    await result.current.mutateAsync({ consultaId: CONSULTA_A, valor: 90.5 })

    expect(corpos).toEqual(['{"valor":90.5}'])
    esperarInvalidacaoDoDinheiro(invalidar)
  })

  it('o financeiro do mes consulta ano e mes na query string', async () => {
    const parametros: { ano: string | null; mes: string | null }[] = []
    servidor.use(
      http.get('/financeiro/mes', ({ request }) => {
        const busca = new URL(request.url).searchParams
        parametros.push({ ano: busca.get('ano'), mes: busca.get('mes') })
        return HttpResponse.json({
          ano: 2026,
          mes: 12,
          faturado: 0,
          recebido: 0,
          aReceber: 0,
          sessoesSemValor: 0,
          sessoes: [],
          devendo: [],
        })
      }),
    )
    const { wrapper } = montar()
    const { result } = renderHook(() => useFinanceiroDoMes(2026, 12), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(parametros).toEqual([{ ano: '2026', mes: '12' }])
  })
})
