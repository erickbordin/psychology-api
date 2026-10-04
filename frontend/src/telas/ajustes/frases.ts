const FRASES: Record<string, Partial<Record<string, string>>> = {
  FichaDoPaciente: { VISUALIZACAO: 'Abriu a ficha' },
  Paciente: {
    CRIACAO: 'Cadastrou paciente',
    VISUALIZACAO: 'Viu o cadastro',
    ATUALIZACAO: 'Editou paciente',
    EXCLUSAO: 'Excluiu paciente',
  },
  Consulta: {
    CRIACAO: 'Agendou sessão',
    VISUALIZACAO: 'Viu sessão',
    ATUALIZACAO: 'Alterou sessão',
    EXCLUSAO: 'Excluiu sessão',
  },
  Anotacao: {
    CRIACAO: 'Registrou anotação',
    VISUALIZACAO: 'Leu anotações',
    EXCLUSAO: 'Excluiu anotação',
  },
  Pendencia: {
    CRIACAO: 'Criou pendência',
    ATUALIZACAO: 'Concluiu pendência',
    EXCLUSAO: 'Excluiu pendência',
  },
  ConsentimentoDoPaciente: { ATUALIZACAO: 'Alterou a autorização de aviso' },
  DescadastroDoPaciente: { ATUALIZACAO: 'Paciente parou de receber lembretes' },
  Ajustes: { ATUALIZACAO: 'Alterou os ajustes' },
  Senha: { ATUALIZACAO: 'Trocou a senha' },
}

export function fraseDaTrilha(acao: string, entidade: string): string {
  return FRASES[entidade]?.[acao] ?? `${acao.toLowerCase()} · ${entidade}`
}
