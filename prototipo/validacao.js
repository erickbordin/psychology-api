/**
 * As regras de validacao do produto, num lugar so.
 * Esta e a saida duravel da Fase 0.5: na Fatia 1 cada funcao daqui vira um
 * campo de schema zod, e os mesmos limites entram nos DTOs do backend.
 * Cada regra devolve `null` quando valido, ou a MENSAGEM quando invalido.
 */
const VALIDACAO = {
  nome(v) {
    const s = (v || '').trim()
    if (!s) return 'Informe o nome do paciente.'
    if (s.length < 2) return 'O nome precisa ter ao menos 2 letras.'
    if (s.length > 100) return 'O nome pode ter no máximo 100 caracteres.'
    return null
  },

  nascimento(v) {
    if (!v) return 'Informe a data de nascimento.'
    const d = new Date(v + 'T00:00:00')
    if (Number.isNaN(d.getTime())) return 'Essa data não existe.'
    const hoje = new Date()
    if (d > hoje) return 'A data de nascimento precisa estar no passado.'
    const anos = (hoje - d) / (365.25 * 24 * 3600 * 1000)
    if (anos >= 120) return 'Confira a data — são mais de 120 anos.'
    return null
  },

  telefone(v) {
    const s = (v || '').trim()
    if (!s) return null                       // opcional
    if (s.length > 20) return 'Telefone pode ter no máximo 20 caracteres.'
    const digitos = s.replace(/\D/g, '')
    if (digitos.length < 10) return 'Telefone incompleto.'
    return null
  },

  /** `obrigatorio` vem true quando o canal de aviso e e-mail (spec §5.4). */
  email(v, obrigatorio) {
    const s = (v || '').trim()
    if (!s) return obrigatorio ? 'Para avisar por e-mail, o endereço é obrigatório.' : null
    if (s.length > 100) return 'E-mail pode ter no máximo 100 caracteres.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)) return 'Informe um e-mail válido.'
    return null
  },

  valor(v) {
    const s = String(v ?? '').trim()
    if (!s) return 'Informe o valor da sessão.'
    const n = Number(s.replace(',', '.'))
    if (Number.isNaN(n)) return 'Informe só números.'
    if (n <= 0) return 'O valor precisa ser maior que zero.'
    if (Math.round(n * 100) !== n * 100) return 'Use no máximo duas casas decimais.'
    return null
  },

  /**
   * Data no passado AVISA mas nao bloqueia — o psicologo precisa registrar
   * sessao que ja aconteceu (spec §6.3). Por isso devolve `{ erro, aviso }`,
   * e nao so uma string: e a unica regra com as duas saidas.
   */
  dataHora(data, hora) {
    if (!data || !hora) return { erro: 'Escolha data e horário.', aviso: null }
    const quando = new Date(`${data}T${hora}`)
    if (Number.isNaN(quando.getTime())) return { erro: 'Data ou horário inválido.', aviso: null }
    if (quando < new Date()) {
      return { erro: null, aviso: 'Essa data já passou — a consulta será registrada como retroativa.' }
    }
    return { erro: null, aviso: null }
  },

  anotacao(v) {
    if (!(v || '').trim()) return 'Escreva a anotação antes de registrar.'
    return null
  },
}

/** Mascara brasileira progressiva: (51) 99999-9999 e (51) 9999-9999. */
function mascaraTelefone(v) {
  const d = (v || '').replace(/\D/g, '').slice(0, 11)
  if (d.length <= 2) return d
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

/**
 * Instala a regra de quando o erro aparece (spec §6.2):
 *  - no blur, so depois do primeiro toque no campo
 *  - no submit, sempre, em todos
 *  - depois que um campo errou, revalida a cada tecla
 * Submit invalido NUNCA e silencioso: marca, move o foco para o primeiro
 * invalido, e anuncia em aria-live.
 *
 * `campos`: [{ id, regra: (valor, form) => string|null }]
 * `aoEnviar`: chamado so quando tudo passa.
 */
function ligarFormulario(form, campos, aoEnviar) {
  const tocados = new Set()
  const comErro = new Set()

  function erroDe(campo) {
    const el = form.querySelector('#' + campo.id)
    return campo.regra(el.value, form)
  }

  function pintar(campo) {
    const el = form.querySelector('#' + campo.id)
    const alvo = form.querySelector(`[data-erro-de="${campo.id}"]`)
    const erro = erroDe(campo)
    if (erro) {
      comErro.add(campo.id)
      el.setAttribute('aria-invalid', 'true')
      el.setAttribute('aria-describedby', alvo.id)
      alvo.textContent = erro
    } else {
      comErro.delete(campo.id)
      el.removeAttribute('aria-invalid')
      el.removeAttribute('aria-describedby')
      alvo.textContent = ''
    }
    return erro
  }

  campos.forEach((campo) => {
    const el = form.querySelector('#' + campo.id)

    // TOQUE E DIGITAR, nao focar. `blur` sozinho e saida de foco: quem passa de
    // Tab por um formulario sem digitar nada nao pode levar erro em todo campo
    // obrigatorio que atravessa — e isso que "nao punir quem ainda esta
    // digitando pela primeira vez" (spec §6.2) quer dizer.
    el.addEventListener('input', () => {
      tocados.add(campo.id)
      // so revalida a cada tecla DEPOIS que este campo ja errou uma vez
      if (comErro.has(campo.id)) pintar(campo)
    })

    el.addEventListener('blur', () => {
      if (!tocados.has(campo.id)) return   // focou e saiu sem digitar: nao valida
      pintar(campo)
    })
  })

  form.addEventListener('submit', (evento) => {
    evento.preventDefault()
    campos.forEach((c) => tocados.add(c.id))
    const erros = campos.map((c) => ({ campo: c, erro: pintar(c) })).filter((r) => r.erro)
    const anuncio = form.querySelector('[data-anuncio]')

    if (erros.length) {
      anuncio.textContent = erros.length === 1
        ? 'Um campo precisa de atenção.'
        : `${erros.length} campos precisam de atenção.`
      form.querySelector('#' + erros[0].campo.id).focus()   // foco no primeiro invalido
      return
    }
    anuncio.textContent = ''
    aoEnviar()
  })

  /**
   * Revalida UM campo cuja obrigatoriedade mudou por causa de outro controle
   * (ex.: o e-mail vira obrigatorio quando o canal de aviso e e-mail).
   * Repinta SO se o campo ja estava em erro — repintar sempre faria aparecer
   * erro em campo que o usuario nunca tocou, que e pior que o defeito.
   */
  return {
    revalidar(id) {
      if (!comErro.has(id)) return
      const campo = campos.find((c) => c.id === id)
      if (campo) pintar(campo)
    },
  }
}
