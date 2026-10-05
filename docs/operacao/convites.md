# Convites de cadastro

A conta só nasce com um **código de convite de uso único**: o dono cobra os primeiros profissionais fora do sistema e entrega o código. Sem a variável `APP_CADASTRO_CONVITES`, nenhum convite vale.

O código nunca vai para log, trilha ou resposta da API. No banco, a tabela `convite_usado` guarda só o **SHA-256** do código, junto com o usuário e o momento do uso.

## Criar um código e liberar

1. Gere o código na VM (ou no seu computador):

```bash
openssl rand -hex 8
```

2. Acrescente o código em `APP_CADASTRO_CONVITES` no arquivo `infra/.env` da VM. A variável é uma lista separada por vírgula, sem espaços:

```
APP_CADASTRO_CONVITES=3f9a1c2b7d4e8a60,a1b2c3d4e5f60718
```

3. Recrie a API para ela ler o valor novo:

```bash
docker compose -f infra/compose.yml --env-file infra/.env up -d api
```

4. Envie o código à pessoa por um canal privado, junto com o endereço `https://{dominio}/cadastro`. Ela cria a conta com o código, aceita os termos e entra direto.

**Prova:** com o código novo, o cadastro responde `201`; com um código inventado, responde `403 Código de convite inválido.`

## Regras que valem

| Situação | Resultado |
|---|---|
| Código usado uma vez | não volta a valer; o segundo uso responde `403` |
| Código desconhecido ou já usado | a mesma mensagem, `Código de convite inválido.`, de propósito: a API não diz qual dos dois |
| Cadastro que falha depois do convite (por exemplo e-mail já cadastrado) | **não** queima o código |
| Dois cadastros ao mesmo tempo com o mesmo código | um recebe `201` e o outro `403`; o índice único do banco decide |
| Código no meio da lista da variável | continua valendo até ser usado; remover da lista o invalida antes |

Código usado pode ficar na lista da variável sem efeito algum, porque o banco já o registrou como usado. Mesmo assim, tire os usados quando for mexer na lista, para ela não crescer para sempre.

## Cuidados

1. O código é um segredo curto: não cole em ticket, conversa de grupo ou arquivo versionado.
2. Cada pessoa recebe um código próprio. Código compartilhado é cadastro de quem você não escolheu.
3. Convite que vazou: remova da lista e rode o `up -d api` de novo. Se já foi usado, não há o que revogar no código; trate a conta como conta comum.
4. O cadastro por convite mitiga a enumeração de e-mails (o `409` de e-mail repetido só aparece para quem tem um convite válido), mas não a elimina. Está registrado como débito.
5. Os textos dos termos precisam estar instalados: sem eles o cadastro responde `503` mesmo com convite válido (veja "Termos de uso" em `implantacao.md`).
6. O limite de tentativas é de 10 por minuto por IP e por rota (`/auth/login`, `/auth/registrar`, `/auth/refresh`); passou disso, a resposta é `429`.
