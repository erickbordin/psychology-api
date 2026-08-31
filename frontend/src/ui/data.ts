const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb']
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/**
 * A API manda tanto `2026-08-19` quanto `2026-08-19T10:00:00`. Recortar antes do
 * `T` e montar em UTC evita o deslocamento de um dia que o `new Date(iso)` faz
 * com data pura em fuso negativo — o Brasil inteiro veria a sessao de ontem.
 * Entrada que nao for data volta como veio, em vez de virar "Invalid Date".
 */
function partes(iso: string): [number, number, number] | null {
  const pedacos = iso.split('T')[0].split('-')
  if (pedacos.length !== 3) return null
  const [ano, mes, dia] = pedacos.map(Number)
  if (!ano || !mes || !dia || mes > 12 || dia > 31) return null
  return [ano, mes, dia]
}

/** `12 abr 1991` — para data de nascimento, onde o dia da semana nao diz nada. */
export function dataCurta(iso: string): string {
  const p = partes(iso)
  if (!p) return iso
  const [ano, mes, dia] = p
  return `${dia} ${MESES[mes - 1]} ${ano}`
}

/** `qua, 19 ago 2026` — sessao acontece em dia da semana, e e assim que se lembra dela. */
export function dataDeSessao(iso: string): string {
  const p = partes(iso)
  if (!p) return iso
  const [ano, mes, dia] = p
  const semana = DIAS[new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay()]
  return `${semana}, ${dia} ${MESES[mes - 1]} ${ano}`
}

/** `15:00` — a hora de um `LocalDateTime` da API, sem conversao de fuso. */
export function hora(iso: string): string {
  const relogio = iso.split('T')[1]
  if (!relogio) return ''
  return relogio.slice(0, 5)
}

/** `qua, 2 set 2026 · 15:00` */
export function dataHora(iso: string): string {
  const relogio = hora(iso)
  return relogio ? `${dataDeSessao(iso)} · ${relogio}` : dataDeSessao(iso)
}

/** Hoje em `AAAA-MM-DD` no fuso de quem esta olhando, que e o dia que importa. */
export function hojeIso(): string {
  const agora = new Date()
  return [
    agora.getFullYear(),
    String(agora.getMonth() + 1).padStart(2, '0'),
    String(agora.getDate()).padStart(2, '0'),
  ].join('-')
}

/**
 * Soma dias a uma data `AAAA-MM-DD` em UTC. E toda a aritmetica de data que o
 * cliente faz — o preview semanal da serie. Quem gera as ocorrencias de verdade
 * e a API; isto so mostra ao psicologo o que ele esta prestes a criar.
 */
export function somarDias(iso: string, dias: number): string {
  const p = partes(iso)
  if (!p) return iso
  const [ano, mes, dia] = p
  const data = new Date(Date.UTC(ano, mes - 1, dia + dias))
  return [
    data.getUTCFullYear(),
    String(data.getUTCMonth() + 1).padStart(2, '0'),
    String(data.getUTCDate()).padStart(2, '0'),
  ].join('-')
}

/** Junta `2026-09-02` e `15:00` no `LocalDateTime` que a API espera. */
export function paraLocalDateTime(data: string, relogio: string): string {
  return `${data}T${relogio.length === 5 ? `${relogio}:00` : relogio}`
}

/** Separa um `LocalDateTime` da API nos dois campos do formulario. */
export function separarDataEHora(iso: string): { data: string; hora: string } {
  return { data: iso.split('T')[0] ?? '', hora: hora(iso) }
}
