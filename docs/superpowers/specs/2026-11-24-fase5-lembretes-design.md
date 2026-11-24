# Fase 5 — Lembretes e WhatsApp (design curto)

Aprovado pelo usuário em 2026-11-24. Base: seções 4.3 e 5 da especificação.

## Intenção
O organizador quer lembrar as pessoas de depositar sem esforço: o app prepara a mensagem de WhatsApp e ele só toca em enviar. Nada é enviado automaticamente. Pronto quando: os cartões aparecem e somem nas regras certas.

## Decisões (do usuário)
- **Uma mensagem só por mês para o grupo**, no dia 1: fecha o mês anterior e anuncia o mês novo (a "virada do mês" e o "lembrete do dia 1" da spec viram um cartão só).
- Aprovado o resto deste documento como está.

## Approaches
1. **Formulário que grava o aviso e redireciona para o wa.me (escolhido).** O botão é um `<form method=post target=_blank>`; o servidor grava o `AvisoMensal`, recalcula o texto e responde com redirect para o `https://wa.me/...`. Funciona sem JavaScript; o texto e o telefone nunca vêm do formulário.
2. Link direto para o wa.me + gravação por JavaScript (`fetch`). Descartado: se o JS falhar, o aviso se perde sem ninguém perceber.

## Cartão do grupo (Início do painel)
- Mês do cartão = mês de hoje, quando estiver entre o 1º mês da caixinha e o mês seguinte ao último (jan/2027 a dez/2027). Fora disso, não há cartão.
- Visível do dia 1 até existir `AvisoMensal` GRUPO daquele mês (pessoa nula).
- Botões: "Enviar no WhatsApp" (`https://wa.me/?text=...`, grava o aviso) e "Copiar" (só copia).
- Texto (WhatsApp, `*negrito*`, sem nomes):
  ```
  *Caixinha da festa* 🎉
  Fechamos *janeiro*: R$ 1.012,00 de R$ 11.440,00 (9%).
  *10 de 11* pagantes estão em dia 💪
  *Fevereiro* começou: a cota é R$ 92,00 por pessoa. O valor da sua família e o PIX estão no seu link pessoal.
  ```
  - 1º mês (janeiro): sem a linha "Fechamos"; diz "A caixinha começou! 🎉" e a cota de janeiro.
  - Mês seguinte ao último (dezembro): fecha novembro e não anuncia mês novo ("Último depósito feito. Agora é festa!").
  - Sem orçamento (cota R$ 0): a frase da cota sai.
  - Números: arrecadado, meta, % e "X de Y pagantes em dia" do resumo do momento em que o cartão é mostrado/enviado.

## Cartão individual (Início do painel)
- De janeiro a novembro, a partir do dia 20.
- Lista as famílias (responsável ativo sem responsável acima dele) cujo estado do mês é PENDENTE ou ATRASADO. Famílias PAGO ou SEM_VALOR não entram; quem paga depois do dia 20 sai da lista.
- Cada linha: nome do responsável e valor; botão "Enviar no WhatsApp" para `https://wa.me/{telefone}?text=...` (sem telefone: `https://wa.me/?text=...`). Tocar grava `AvisoMensal` INDIVIDUAL (mês, responsável).
- Já avisado: "Avisado ✓" com botão "desfazer" (apaga o aviso daquele mês e pessoa).
- O cartão some quando todos da lista têm aviso INDIVIDUAL no mês.
- Texto:
  ```
  Oi, Ana! 😊 Lembrete da caixinha da festa: a cota de *março* da família é *R$ 138,00*.
  O PIX já está pronto no seu link: https://…/c/…
  ```
  - Com 2 ou mais meses de atraso em algum pagante da família, mais uma linha: "Para ficar em dia: *R$ 414,00*. Se precisar combinar algo, é só me chamar."
  - Manda o link pessoal (que tem o PIX), não o código do PIX — a spec permite os dois.

## Regras gerais
- Gravar o aviso é idempotente (tocar duas vezes não duplica).
- O servidor só aceita avisos para o mês do cartão visível hoje.
- O botão da tela Pessoas (envio do link) continua sem gravar aviso.
- Sem migração: a tabela `aviso_mensal` existe desde a V1.

## Fica com quem implementa
Nomes de classes, layout dos cartões dentro do padrão visual do painel, recarregar a aba do painel depois do toque.
