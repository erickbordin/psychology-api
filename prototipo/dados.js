const DADOS = {
  usuario: { nome: 'Marina Aguiar', valorPadrao: 180, antecedencia: '24h' },
  hoje: {
    data: 'terça, 1 de setembro',
    agora: '08:40',
    sessoes: [
      { hora: '09:00', paciente: 'Helena Vaz',    status: 'agendada', faltouAntes: true },
      { hora: '10:00', paciente: 'Rui Sampaio',   status: 'agendada' },
      { hora: '11:00', paciente: 'Clara Bueno',   status: 'agendada' },
      { hora: '14:00', paciente: 'Otávio Lins',   status: 'agendada' },
      { hora: '15:00', paciente: 'Bruna Teixeira',status: 'agendada' },
      { hora: '16:00', paciente: 'Ivan Prado',    status: 'agendada' },
    ],
    pendencias: [
      { tipo: 'sem-status',   texto: 'Rui Sampaio · ontem 10:00' },
      { tipo: 'sem-status',   texto: 'Clara Bueno · ontem 11:00' },
      { tipo: 'sem-anotacao', texto: 'Otávio Lins · ontem 14:00' },
      { tipo: 'sem-baixa',    texto: 'Bruna Teixeira · 4 sessões' },
    ],
  },
  // fix round 1 (Task 4): telefone/dataNascimento/email entram aqui — a
  // spec (§5.4) já tem esses campos na entidade Paciente, o que estava
  // pobre era o dado falso do protótipo, não o desenho (trava [fatia], não
  // [escopo]). Sem isso "Editar" abria a Helena com os campos em branco e
  // parecia quebrado bem na feature que esta tarefa entrega.
  // Rui já tem consentimento datado ('2026-08-02') → precisa de e-mail,
  // senão o dado se contradiz (não dá pra ter ligado o aviso por e-mail sem
  // e-mail). Clara tem consentimento null → e-mail null, de propósito, pra
  // servir de contraste: e-mail só é obrigatório quando o canal do aviso é
  // e-mail, não só por ter telefone/nascimento preenchidos.
  pacientes: [
    { id: 1, nome: 'Helena Vaz', desde: 'março de 2026', valor: 180, consentimento: null,
      telefone: '(51) 99811-2345', dataNascimento: '1992-01-20', email: 'helena.vaz@example.com' },
    { id: 2, nome: 'Rui Sampaio', desde: 'janeiro de 2026', valor: 180, consentimento: '2026-08-02',
      telefone: '(51) 98123-4567', dataNascimento: '1985-06-10', email: 'rui.sampaio@example.com' },
    { id: 3, nome: 'Clara Bueno', desde: 'agosto de 2026', valor: 200, consentimento: null,
      telefone: '(51) 99456-7890', dataNascimento: '1998-11-02', email: null },
  ],
  // Task 7 (§5.3, decisão do dono de 4/9/2026): falta não é cobrada. O
  // Financeiro agrega só consulta com status `realizada` — falta não entra
  // em faturado, não entra em a receber, e não põe ninguém em "devendo".
  // A sessão em que a Helena faltou (linhaTempo, 25 ago) mantém `valor: 180`
  // (o snapshot registra quanto a hora valia; apagar perderia informação),
  // mas quem soma dinheiro para de contar essa sessão.
  // Ajuste: Helena sai de "devendo"; "aReceber" cai de 1440 para 1260 (só
  // Bruna + Ivan); "faturado" cai de 4500 para 4320 (a sessão da Helena
  // deixa de ser faturada); "recebido" fica igual — ninguém recebeu nada de
  // novo, a mudança é só sobre o que é cobrado, não sobre o que já foi pago.
  // Confere: recebido (3060) + aReceber (1260) = faturado (4320);
  // aReceber (1260) = soma de "devendo" (720 + 540).
  //
  // Histórico (por que existe este comentário): até a Task 6, esta mesma
  // sessão da Helena entrava em "devendo" e inflava "faturado" para 4500 —
  // regra que nunca foi decidida por ninguém, entrou por reconciliação de
  // dado (ficha × Financeiro) na Task 6, virou trava `[decisão do dono]` em
  // TRAVAS.md, e o dono a resolveu em 4/9/2026: falta não é cobrada. Fica
  // registrado que a regra antiga existiu — um comentário que fingisse que
  // "sempre foi assim" enganaria quem lesse depois.
  // "mes" segue 'agosto' (corrigido na Task 6): as datas da tela inteira
  // (devendo, sessões da semana passada, a falta da Helena) são todas de
  // agosto — "hoje" é 1º de setembro (DADOS.hoje), então o fechamento do C3
  // ("é dia 30") é do mês anterior, agosto, não setembro.
  financeiro: {
    mes: 'agosto', faturado: 4320, recebido: 3060, aReceber: 1260,
    devendo: [
      { paciente: 'Bruna Teixeira', sessoes: 4, valor: 720 },
      { paciente: 'Ivan Prado',     sessoes: 3, valor: 540 },
    ],
  },
}

// Task 5 — ficha da Helena Vaz (paciente id 1): linha do tempo funde consulta
// e anotação numa linha só por encontro (spec §4 ③, §5.1), no lugar das
// antigas abas "Consultas" e "Anotações".
// Coerência com CENARIOS.md/C2 e com hoje.sessoes (faltouAntes: true p/
// Helena): "faltou sem avisar na semana passada" é a sessão mais recente
// antes de hoje (25 ago), não uma sessão intermediária — por isso o
// status/pago/anotação de 25 ago e 18 ago vêm trocados em relação ao
// rascunho literal do brief (mesmos valores de campo, só reordenados).
DADOS.linhaTempo = [
  { data: '25 ago', status: 'faltou', valor: 180, pago: false, anotacao: null },
  { data: '18 ago', status: 'realizada', valor: 180, pago: true,
    anotacao: 'Trouxe o registro de sono da semana. Dormiu mal nas noites de domingo.' },
  { data: '11 ago', status: 'realizada', valor: 180, pago: true,
    anotacao: 'Combinamos que ela anota o sono até a próxima sessão.' },
]
DADOS.pendenciasPaciente = [
  { texto: 'Retomar o registro de sono', concluida: false },
]

/** O dia 1 do psicologo pagante: zero de tudo. A Fase 0 nao desenhou isto em
    tela nenhuma, e e de onde "amador" costuma vir. Chaves conferidas contra
    DADOS acima (usuario/hoje/pacientes/financeiro) e contra DADOS.linhaTempo/
    DADOS.pendenciasPaciente logo acima (Task 5) — sem isso o alternador do
    Step 3 deixaria dado velho vazando na tela vazia. */
DADOS.vazio = {
  usuario: { nome: 'Marina Aguiar', valorPadrao: 180, antecedencia: '24h' },
  hoje: { data: 'segunda, 7 de setembro', agora: '08:40', sessoes: [], pendencias: [] },
  pacientes: [],
  financeiro: { mes: 'setembro', faturado: 0, recebido: 0, aReceber: 0, devendo: [] },
  linhaTempo: [],
  pendenciasPaciente: [],
}
