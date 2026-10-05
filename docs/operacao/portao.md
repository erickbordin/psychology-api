# Portão: o que não é código

A Fatia 5 inteira só fecha quando esta tabela fechar. A 5A fecha quando o código, os roteiros e o deploy estiverem prontos e a restauração real tiver sido executada uma vez.

`Estado` começa `aberto`. Ao fechar um item, troque para `fechado` e preencha a `Evidência` com data e resultado, **sem dado pessoal, segredo, chave, endpoint ou código de convite**.

| Item | Dono | Bloqueia | Estado | Evidência |
|---|---|---|---|---|
| Registrar o domínio `.com` e apontar o DNS para a VM (sugestão: Cloudflare como registrador e DNS) | dono | deploy | aberto | |
| Conta Oracle Cloud, VM ARM em São Paulo, bucket privado e chaves de acesso ao bucket | dono, pelo `docs/operacao/implantacao.md` | deploy | aberto | |
| Par de chaves `age` do backup gerado fora da VM; privada guardada fora dela | dono | restauração real | aberto | |
| Chave do Object Storage em usuário IAM dedicado só ao bucket | dono | 1º pagante | aberto | |
| Restauração real executada e registrada | dono, pelo `docs/operacao/restauracao.md` | 1º pagante | aberto | |
| Monitor externo de `/api/saude` | dono | 1º pagante | aberto | |
| Termo de Uso, Política de Privacidade, contrato de operador (LGPD art. 39) | advogado | 1º pagante | aberto | |
| Prazo de guarda do prontuário (CFP) em fonte primária | dono/advogado | 5B (purga) | aberto | |
| Plano de incidente: parte técnica escrita na 5A (`docs/operacao/incidente.md`); prazos e texto da comunicação à ANPD e aos titulares **[verificar]** com advogado (a Resolução CD/ANPD nº 15/2024 é a referência a confirmar) | Claude (técnica) + advogado | 1º pagante | aberto | |
| Provedor de e-mail + SPF/DKIM/DMARC no domínio | dono, quando decidir | só o aviso | aberto | |
| Homologação com TI/Infra, se o projeto for da Syonet | dono | — | aberto | |
