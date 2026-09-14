import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, type FieldValues, type UseFormProps } from 'react-hook-form'
import type { z } from 'zod'

import { ErroDaApi } from '../api/erro'

/**
 * As tres regras da spec 6.2, num lugar so:
 *
 *   `mode: 'onTouched'`        — no blur, SO depois do primeiro toque
 *   `reValidateMode: 'onChange'` — depois que errou, revalida a cada tecla
 *   `shouldFocusError: true`   — submit invalido poe foco no primeiro invalido
 *
 * O submit sempre valida tudo: e o comportamento padrao do handleSubmit, e e a
 * terceira regra. O `if (!x) return` que morria em silencio nao volta.
 */
// O generico e amarrado a `FieldValues`, e nao e detalhe de estilo: com
// `E extends z.ZodType` puro, `z.output<E>` e `unknown`, que nao satisfaz a
// restricao do `useForm`, e o `tsc -b` do `npm run build` quebra. A suite NAO
// pega isso — o Vitest tira os tipos com esbuild e nao checa nada — entao o
// projeto fica verde com o build quebrado. Foi assim que este defeito nasceu.
export function useFormularioDoProduto<Valores extends FieldValues>(
  esquema: z.ZodType<Valores, Valores>,
  aoEnviar: (valores: Valores) => Promise<void> | void,
  opcoes: Omit<UseFormProps<Valores>, 'resolver' | 'mode' | 'reValidateMode'> = {},
) {
  const [erroDeEnvio, setErroDeEnvio] = useState<ErroDaApi | null>(null)
  const [enviando, setEnviando] = useState(false)

  const formulario = useForm<Valores>({
    ...opcoes,
    resolver: zodResolver(esquema),
    mode: 'onTouched',
    reValidateMode: 'onChange',
    shouldFocusError: true,
  })

  async function enviar(valores: Valores) {
    setErroDeEnvio(null)
    setEnviando(true)
    try {
      await aoEnviar(valores)
    } catch (erro) {
      // O texto do SERVIDOR vence o do zod: ele conhece regras que o cliente
      // nao tem como checar. Cada campo culpado vira erro daquele campo.
      if (erro instanceof ErroDaApi) {
        setErroDeEnvio(erro)
        for (const { campo, erro: mensagem } of erro.erros) {
          formulario.setError(campo as never, { type: 'server', message: mensagem })
        }
      } else {
        throw erro
      }
    } finally {
      setEnviando(false)
    }
  }

  return { ...formulario, enviar, erroDeEnvio, enviando }
}
