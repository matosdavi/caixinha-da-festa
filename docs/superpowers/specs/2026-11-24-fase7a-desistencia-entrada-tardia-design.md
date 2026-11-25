# Fase 7, parte 1 — Desistência e entrada depois do fechamento da lista (design)

Base: seções 3.5 e 3.6 da especificação. A Fase 7 foi dividida em 4 partes, nesta ordem (decisão do usuário): (1) desistência e entrada tardia, (2) desconto de novembro, (3) prestação de contas e escolha da sobra, (4) teste de ponta a ponta com saldo final R$ 0,00.

## Intenção
A partir de janeiro de 2027 a lista está fechada e a cota fixada. Alguém pode desistir ou entrar depois. O organizador precisa de uma conta justa de devolução e de um jeito de cobrar de quem entra tarde o acréscimo que o anfitrião pedir, sem mexer na cota dos outros.

## Decisões do usuário
- O acréscimo de quem entra tarde é **dividido nos meses que faltam** (não cobrado de uma vez).
- Guardado em **duas colunas na pessoa** (não tabela própria, não tipo de lançamento).
- "Antes/depois do sinal" da spec vira **"antes/depois de pagar o lugar"** (Fase 6: o lugar é pago inteiro na reserva).

## Approaches (onde guardar o acréscimo)
1. **Colunas `acrescimo` e `acrescimo_desde` em `pessoa` (escolhido).** Um acréscimo por pessoa, editável.
2. Tabela própria de acréscimos: vários por pessoa, com descrição. Mais flexível, mais trabalho; a spec fala em um valor por pessoa.
3. Novo tipo de lançamento: descartado — lançamento é dinheiro que entrou/saiu e o saldo precisa bater com o extrato.

## Banco (migração V3 — produção)
- `pessoa.acrescimo NUMERIC(12,2) NULL CHECK (acrescimo >= 0)` — nulo = ainda não confirmado com o anfitrião; 0 = confirmado, sem acréscimo.
- `pessoa.acrescimo_desde VARCHAR(7) NULL` — mês ('AAAA-MM') em que a divisão começa.
- ⚠️ Antes do deploy: branch/backup no Neon e aviso ao usuário (regra da spec). V1 e V2 não são alteradas.

## Valor de cada mês (generalização da conta)
Hoje o esperado de uma pessoa é `cota × meses`. Passa a ser a **soma dos valores mês a mês**:
- `valorDoMes(p, m) = cota × peso + parcelaDoAcrescimo(p, m)`.
- Parcela: o acréscimo dividido igualmente pelos meses de `acrescimo_desde` até o último mês (inclusive), em centavos para baixo; os centavos que sobram vão para o último mês. Ex.: R$ 300 desde julho → 60 × 5; R$ 100 em 3 meses → 33,33 + 33,33 + 33,34.
- `acrescimo_desde` = mês de hoje quando o acréscimo é registrado pela primeira vez com valor > 0, limitado ao intervalo da caixinha (depois de novembro → novembro, tudo de uma vez). Mudar o valor depois mantém o `desde`; zerar limpa o `desde`.
- `esperadoAteAgora` = soma dos meses fechados; `esperadoComMesAtual` = soma até o mês atual; `faltaNoMes` = mín(paraFicarEmDia, valor do mês atual); `mesesDeAtraso` = quantos meses fechados não estão cobertos pelo guardado (igual ao de hoje quando o valor é constante).
- Carnê: cada talão usa o valor daquele mês (a família soma os membros). "Cota de março da família" nas telas e no lembrete do dia 20 passa a ser o valor de março (cota + parcela).
- A meta e a cota base não mudam. A parte 2 (desconto de novembro) vai usar o mesmo mecanismo para o último mês.

## Entrada tardia (painel)
- "Entrou tarde" = `data_entrada` depois de `data_fechamento_lista`.
- **Cartão no Início** "Quem entrou depois do lugar pago": aparece quando o lugar está pago (mesmo critério do "✅ Lugar pago" do Cronograma) e há pessoa ativa pagante que entrou tarde com `acrescimo` nulo. Texto: "Confirme com o anfitrião a lotação da casa e se há acréscimo por essa pessoa." Para cada uma: campo "Acréscimo (R$)" e botão "Salvar" (0 = sem acréscimo; some do cartão).
- O acréscimo também aparece e pode ser editado no formulário da pessoa (campo "Acréscimo cobrado pelo anfitrião", só para quem entrou tarde), com a ajuda "Dividido nos meses que faltam, a partir do mês em que foi registrado."
- Página pessoal: o carnê já mostra o valor maior nos meses com parcela; uma linha "Inclui R$ X de acréscimo do lugar, dividido até novembro." quando a família tem acréscimo.

## Desistência (painel)
- Botão "Desistência" na linha da pessoa (tela Pessoas), para pessoa ativa. Abre `/painel/pessoas/{id}/desistencia`.
- A tela mostra: quanto a pessoa guardou; se o lugar já foi pago; a sugestão de devolução; caixa "Outra pessoa vai assumir a vaga".
- Sugestão:
  - lugar ainda não pago, ou outra pessoa assume a vaga → **tudo o que ela guardou**;
  - lugar pago → **parte dela nos gastos variáveis** = (COMIDA + BEBIDA + GELO_CARVAO_DESCARTAVEIS, valor real quando existir, senão previsto) × peso dela ÷ soma dos pesos das pessoas ativas (com ela), arredondado para baixo no centavo, **limitado ao que ela guardou**.
- Campos: valor da devolução (já com a sugestão, editável, 0 a guardado) e data (hoje). Botão "Confirmar desistência" com confirmação ("Lançar devolução de R$ X e tirar Fulana da caixinha?").
- Ao confirmar, numa transação: lança DEVOLUCAO (se valor > 0) com descrição "Desistência"; marca a pessoa inativa (regras de hoje: links desativados; responsável com dependentes ativos não pode — mensagem atual). A cota dos outros não muda (cota fixada); antes de fixar, a estimativa recalcula como hoje.
- Quem assume a vaga é cadastrado normalmente em Pessoas (entra tarde, deve desde janeiro).

## Fora desta parte
Desconto de novembro (parte 2), prestação de contas e escolha da sobra (parte 3), teste de ponta a ponta (parte 4).

## Fica com quem implementa
Nomes de classes e layout dentro do padrão do painel; como o carnê recebe os valores mês a mês.
