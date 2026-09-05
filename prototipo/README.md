# Protótipo — Fase 0

Descartável. Só os tokens (`tokens.css`) e o mapa de telas sobrevivem
para a Fatia 1; o resto é apagado quando o front novo tiver as cinco telas.

Os tokens de espaçamento (`--gap-1` a `--gap-7`) e de raio (`--raio`,
`--raio-g`) foram **inventados nesta fase** — não existem no `tokens.css` de
origem (`frontend/src/estilo/tokens.css`). A Fatia 1 precisa decidir se eles
viram tokens de tema do Tailwind ou ficam como custom properties; sem essa
decisão, cada componente React reinventa o próprio espaçamento.

Rodar: `python3 -m http.server 8000 --directory prototipo`

- `CENARIOS.md` — os quatro percursos que este protótipo precisa aguentar
- `TRAVAS.md` — o que travou, e o que foi feito

**Isto não é um produto validado.** Foi percorrido contra cenário escrito, não
usado por psicólogo nenhum. Ver spec §9, Fase 0.
