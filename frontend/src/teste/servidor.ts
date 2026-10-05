import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

export const servidor = setupServer(
  http.post('/auth/refresh', () => new HttpResponse(null, { status: 401 })),
)
