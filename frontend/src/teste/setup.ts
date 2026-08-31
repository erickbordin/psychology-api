import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { servidorDeTeste } from './servidor'

beforeAll(() => servidorDeTeste.listen({ onUnhandledRequest: 'error' }))
afterEach(() => servidorDeTeste.resetHandlers())
afterAll(() => servidorDeTeste.close())
