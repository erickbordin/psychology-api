# Cenários — Fase 0

Percursos que o protótipo precisa aguentar. Cada tarefa de tela cita o seu.
Não são casos de teste: são o dia de alguém.

## C1 — Antes do expediente (tela Hoje)

São 8h40 de uma terça. Tenho 6 sessões hoje, a primeira às 9h.
A paciente das 9h faltou sem avisar na semana passada — preciso saber disso
antes de ela entrar na sala, e não depois.
Ontem atendi 5 pessoas e esqueci de marcar presença em duas.

Preciso responder, sem clicar em nada:
- quem é a próxima e daqui a quanto tempo
- quantas sessões o dia tem
- o que ficou pendente de ontem

E preciso conseguir, em um clique: fechar a pendência de ontem.

## C2 — Antes e depois da sessão (Pacientes e Ficha)

São 8h55. Abro a ficha da Helena Vaz, que atendo às 9h.
Ela vem desde março, são 14 sessões até aqui, e faltou sem avisar na semana
passada.

Preciso lembrar, sem caçar: o que ficou combinado na última sessão, há quanto
tempo ela vem, quantas vezes já faltou, e se tem alguma pendência que eu mesmo
anotei.

São 9h52, a sessão acabou. Preciso, sem sair da ficha:
registrar a anotação desta sessão, marcar que ela aconteceu, e agendar o
retorno para a próxima terça no mesmo horário.

## C3 — Fechamento do mês (Financeiro)

É dia 30. Quero saber quanto faturei, quanto entrou de fato, e quem está
devendo.
Duas pessoas pagam no cartão e sempre atrasam; uma paga por sessão, em dinheiro,
no dia.
Preciso dar baixa em 4 sessões da semana passada sem abrir 4 telas.

## C4 — Ligar o aviso (Ficha e Ajustes)

*Sem fato do passado, de propósito: a paciente foi cadastrada hoje. O que dá
mordida aqui é a aposta concreta — o marido usa o mesmo computador — e a
escolha de antecedência.*

Cadastrei uma paciente nova hoje. Ela topou receber lembrete da sessão por
e-mail.
Preciso registrar esse consentimento, escolher a antecedência do aviso: 24 horas antes, e ver como a mensagem vai chegar para ela — inclusive o assunto, porque
o marido dela usa o mesmo computador.

## C5 — Paciente novo, com o paciente na frente (Ficha e cadastro)

São 14h05. A sessão das 14h é uma primeira consulta: Diego Fontes, 28 anos,
chegou por indicação. Ele está sentado na minha frente e eu cadastro enquanto
conversamos.

Tenho o telefone dele. O e-mail ele não lembra de cabeça, vai mandar depois.
A data de nascimento ele me diz falando, não escrevendo.
Combinei R$ 200 por sessão com ele, não os R$ 180 que eu cobro dos outros.

Ele topou receber lembrete da consulta. Preciso registrar isso — e o sistema
precisa me impedir de ligar o aviso por e-mail sem ter o e-mail dele.

## C6 — Marcar a próxima, com a agenda cheia (Nova consulta e retorno)

São 14h52, a sessão do Diego acabou. Marco o retorno dele.

Quero a próxima terça, mesmo horário. A terça que vem, às 14h, já tem alguém.
Preciso descobrir isso **antes** de tentar salvar, não depois de levar erro.

Depois preciso lançar uma sessão que já aconteceu — atendi a Helena na quinta
passada num encaixe e nunca registrei. É data no passado, e o sistema tem que
deixar, avisando que é retroativa.

## C7 — Primeiro dia de uso (estados vazios)

*Sem hora cravada, de propósito: o que reprova tela aqui é a ausência total de
dado, não o horário. Um consultório vazio às 8h40 e às 15h é o mesmo consultório
vazio.*

Instalei hoje. Não tenho paciente nenhum, não tenho sessão nenhuma, não faturei
nada. É segunda-feira de manhã.

Abro o sistema e preciso entender, sem ler manual: que ele está vazio porque eu
sou novo, não porque quebrou — e qual é a primeira coisa que eu faço.
