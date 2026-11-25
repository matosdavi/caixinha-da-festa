# Fase 6 — Cronograma e verificação do pagamento do lugar (design curto)

Aprovado pelo usuário em 2026-11-24. Base: seções 6 e 7 da especificação. Orçamento (previsto/real, meta, "Fixar cota") já foi entregue na Fase 3.

## Intenção
Dar ao organizador um cronograma que recalcula ao mudar a data da festa, avisar cedo se a caixinha não vai ter dinheiro para pagar o lugar, e mostrar a próxima etapa para a família.

## Decisões do usuário (contrariam ou completam a spec)
- **Pagamento do lugar = aluguel inteiro na reserva** (Airbnb não tem sinal). A verificação compara com **100%** do aluguel, não 50%. Sem escolha "sinal × tudo" agora (evita migração); se fecharem uma chácara com sinal, vira mudança pequena depois.
- **Antecedência:** festa a partir de 20/12 (ou em janeiro, réveillon) → procurar o lugar 7 meses antes e reservar 6 meses antes. Antes de 20/12 → 6 e 5 meses (regra da spec para 18–19/12). A família não pretende escolher data antes do dia 20.

## Approaches
1. **Sempre 100% do aluguel, sem coluna nova (escolhido).** Sem migração no banco de produção.
2. Escolha "tudo na reserva / sinal de X%" guardada na configuração. Descartado agora: exige migração V3 + backup no Neon para um caso que provavelmente não acontece.

## Tela Cronograma (painel, novo item do menu)
- Campo da data da festa (pode limpar = volta para "Data em votação"). Data aceita: de 1º/12/2027 a 31/01/2028 (depois do último depósito; janeiro cobre o réveillon). Fora disso: "A festa precisa ser depois do último depósito, em dezembro de 2027 ou em janeiro de 2028."
- Etapas (cada uma com mês, título, detalhe e estado ✅ feito / 👉 agora / próxima, comparando o mês de hoje com o mês da etapa):

| Mês | Título | Detalhe |
|---|---|---|
| mês do fechamento da lista (dez/2026) | Fechar a lista e fixar a cota | até 31/12/2026 |
| 1º mês (jan/2027) | Regras combinadas, data da festa e 1º depósito | enquete para a data |
| festa − 7 (ou − 6) meses | Procurar o lugar | 3 opções, o que está incluso, taxa de limpeza, lotação (bebê conta?), horários e regras de som |
| festa − 6 (ou − 5) meses | Reservar e pagar o lugar | ler a política de cancelamento antes de pagar |
| penúltimo mês (out/2027) | Cardápio, tarefas e lista de compras | calcular o desconto de novembro |
| último mês (nov/2027) | Último depósito e sorteio do amigo secreto | o depósito de novembro já vem com o desconto |
| mês da festa | Festa! 🎉 | compras de perecíveis; prestação de contas em até 7 dias |

- Sem data: "Data em votação" e só as 2 primeiras etapas.

## Verificação do pagamento do lugar (na etapa "Reservar e pagar o lugar")
- `previsto = arrecadaçãoMensal × meses do 1º mês até o mês da reserva (inclusive) + rendimentos + doações − gastos` (valores de hoje).
- `aluguel = soma dos itens ALUGUEL, usando o valor real quando existir, senão o previsto`.
- Sem aluguel no orçamento: "Cadastre o aluguel no orçamento para conferir."
- Cobre: "Em junho a caixinha deve ter R$ 6.348,00 e o lugar custa R$ 6.000,00. ✅ Dá para pagar."
- Não cobre: alerta "Em maio a caixinha deve ter R$ 5.290,00 e o lugar custa R$ 6.000,00: faltam R$ 710,00." + sugestões: reservar um pouco mais tarde; ver se o anúncio aceita "pague uma parte agora"; pedir adiantamento a quem puder; aumentar a margem.

## Próxima etapa
- = primeira etapa que não está feita (a do mês atual conta). Sem nenhuma: sem linha.
- Página pessoal: "Próxima etapa: procurar o lugar, em maio."
- Mensagem do grupo do dia 1: linha final "📅 Próxima etapa: *procurar o lugar* (maio)".

## Fora desta fase
Desistência (Fase 7): "antes/depois do sinal" vira "antes/depois de pagar o lugar".

## Fica com quem implementa
Nomes de classes, layout no padrão visual do painel.
