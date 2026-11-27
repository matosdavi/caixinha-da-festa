# Fase 7, parte 3 — Prestação de contas e escolha da sobra (design)

Base: seção 3.7 da especificação. Decisões do usuário em 2026-11-27: aceitas as propostas abaixo; **o crédito para o ano seguinte é lançado como DEVOLUCAO** (o saldo deste ano fecha em R$ 0,00).

## Intenção
Depois da festa, mostrar para cada família quanto sobrou e qual a parte dela, deixar a família escolher entre receber de volta ou deixar para o ano seguinte, e dar ao organizador o caminho para lançar tudo até o saldo fechar em R$ 0,00.

## Conta
- Lançamentos "da sobra" = DEVOLUCAO com descrição que começa com **"Sobra da festa"** ou **"Crédito para "** (lançados pelos botões desta tela). Eles ficam **fora** da conta, para as partes não mudarem enquanto o organizador lança.
- `sobraFinal = entradas − gastos − devoluções` (sem os lançamentos da sobra) = saldo de hoje + lançamentos da sobra já feitos.
- `guardado(p)` = depósitos + crédito do ano anterior − devoluções (sem os da sobra). Entram todas as pessoas com guardado > 0, inclusive inativas.
- `parte(p) = sobraFinal × guardado(p) ÷ Σ guardado`, em centavos para baixo; os centavos que faltam vão, um a um, para quem guardou mais (empate: menor id), para a soma bater exatamente com a sobra.
- Sobra ≤ 0: sem partes (tela informa "Não sobrou dinheiro"); nada a escolher.
- A parte de quem adiantou novembro já é maior porque o guardado é maior (resolve o crédito de quem pagou antes do desconto).

## Quando
- Aparece depois da data da festa (`hoje > data_festa`); sem data, não aparece.
- Prazo da escolha: data da festa + 7 dias (inclusive). Até o prazo a família escolhe e pode mudar. Depois, quem não escolheu conta como **CREDITO**.

## Página pessoal (`/c/{token}`, depois da festa)
- Seção "Prestação de contas": gastos lançados por categoria (com total), total arrecadado, sobra.
- "A parte da sua família: R$ X" (soma das partes do responsável e dependentes).
- Dois botões (POST, únicos que o link pessoal faz): **"Quero receber de volta"** e **"Deixar para o ano que vem"**; grava `EscolhaSobra` com a mesma opção para todos da família. Mostra a escolha atual e o prazo. Depois do prazo, só mostra a escolha final.
- Família com parte 0: só o resumo.

## Painel — tela "Prestação de contas"
- Resumo: entradas, gastos por categoria, devoluções, sobra final, prazo.
- Lista por pessoa: guardado, parte, escolha (Devolver / Crédito / "ainda não escolheu → crédito"), seletor para o organizador mudar a escolha (ex.: quem não participa no ano seguinte → Devolver), e o lançamento: botão **"Lançar"** que cria a DEVOLUCAO da parte com descrição "Sobra da festa" (devolver) ou "Crédito para AAAA" (crédito; AAAA = ano do último mês + 1). Quem já foi lançado mostra ✅.
- Lançar só depois do prazo (antes, as escolhas ainda mudam).
- **Exportar créditos (CSV)**: nome;valor das pessoas com crédito, separador ponto e vírgula, valores com vírgula (abre no Excel em pt-BR).
- **Saldo final** em destaque: "✅ Contas fechadas: saldo R$ 0,00" quando zera; senão o valor que falta.
- Item no menu "Contas" (aparece sempre; antes da festa a tela explica que abre depois da festa).

## Banco
Sem migração: `escolha_sobra` existe desde a V1.

## Approaches
1. **Lançamentos da sobra marcados pela descrição (escolhido)**: sem migração; os botões controlam a descrição.
2. Tabela de "fechamento" congelando as partes: mais robusto a edições manuais, mas exige migração. Descartado.
