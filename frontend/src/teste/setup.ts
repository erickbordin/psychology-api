import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { servidor } from './servidor'

// `error` e de proposito: chamada que nenhum handler cobre vira FALHA, nao
// silencio. Teste que bate num endpoint esquecido deve ficar vermelho.
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }))
afterEach(() => servidor.resetHandlers())
afterAll(() => servidor.close())
