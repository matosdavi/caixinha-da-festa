# Fase 7, parte 2 — Desconto de novembro (design curto)

Aprovado pelo usuário em 2026-11-27 ("aceito todas as suas sugestões"). Base: seção 3.4 da especificação.

## Intenção
Em outubro o organizador calcula quanto vai sobrar e devolve metade como desconto no último depósito (o "presente do amigo secreto"); a outra metade fica de reserva.

## Conta (seção 3.4)
- `faltaDaPessoa = max(0, soma dos valores dos meses até novembro (com acréscimo) − guardado)` para cada pagante ativo; quem adiantou conta 0.
- `arrecadacaoPrevistaFinal = guardado de todos (inclui inativos, já líquido de devoluções) + rendimentos + doações + Σ faltaDaPessoa`.
- `gastosPrevistos = Σ itens do orçamento (real quando existe, senão previsto)`.
- `sobraPrevista = arrecadacaoPrevistaFinal − gastosPrevistos`; `reserva = sobra − 2 × (metade)`… mais simples: metade da sobra é desconto, metade é reserva.
- `descontoPorPeso = floor((sobra ÷ 2) ÷ somaPesos dos ativos)` em reais inteiros; sobra ≤ 0 → 0 e não dá para confirmar.
- Novembro de cada pessoa: `valorNov − min(cotaDaPessoa, descontoPorPeso × peso)` — o desconto sai da cota, a parcela do acréscimo continua; nunca negativo.

## Regras
- Exige cota fixada ("Fixe a cota antes de calcular o desconto.").
- Prévia a qualquer momento depois de fixar a cota; **confirmar, recalcular e desfazer só no penúltimo mês (outubro de 2027)**. Fora disso: "O desconto de novembro se confirma em outubro." / a partir de novembro: "O desconto já está valendo e não muda mais."
- Grava `caixinha.desconto_novembro_por_peso` (coluna existe desde a V1; sem migração). Desfazer grava nulo.

## Onde aparece
- Tela **Desconto de novembro** no painel (`/painel/desconto-novembro`): prévia com as parcelas da conta, desconto por peso, exemplo para cota inteira e meia cota, reserva; botões Confirmar / Desfazer.
- Etapa de outubro do Cronograma ganha link para a tela.
- Carnê, PIX, situação e lembrete do dia 20 de novembro usam o novembro com desconto (via a conta de valor de cada mês da parte 1).
- Página pessoal: "Novembro com desconto: R$ X (presente do amigo secreto 🎁)" com o valor de novembro da família, quando há desconto.

## Approaches
Uma só sensata: conta pura na `CalculadoraCaixinha` (desconto aplicado ao último valor da lista de meses) + serviço que monta a prévia. Guardar o valor de novembro por pessoa seria redundante — ele sai da cota, do peso e do desconto por peso.
