# Fase 7, parte 1 — Desistência e entrada tardia — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** acréscimo de quem entra tarde dividido nos meses que faltam (com cartão no Início e campo na pessoa) e tela de desistência com devolução sugerida.

**Architecture:** migração V3 com duas colunas em `pessoa`. A conta de situação e o carnê passam a trabalhar com uma lista de valores por mês (`CalculadoraCaixinha.valoresDosMeses`), mantendo as assinaturas atuais como atalhos para valor constante. `DesistenciaService` calcula a sugestão e confirma (DEVOLUCAO + inativar).

**Tech Stack:** Spring Boot 4.0 (Java 17 APIs), Flyway, JPA, Thymeleaf, H2 (PostgreSQL mode) e PostgreSQL embutido (zonky) nos testes.

**Spec:** `docs/superpowers/specs/2026-11-24-fase7a-desistencia-entrada-tardia-design.md`

## Global Constraints
- Build: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; ./mvnw -q test`. Só APIs do Java 17.
- Nova migração `V3__acrescimo_da_pessoa.sql`; **nunca** editar V1/V2. Precisa rodar em H2 modo PostgreSQL e no PostgreSQL real (MigracoesPostgresTest).
- Dinheiro: `CalculadoraCaixinha.dinheiro` (HALF_UP) para totais; parcelas do acréscimo em centavos para baixo com o resto no último mês.
- Textos em pt-BR, gênero neutro. Commits pequenos em português com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Deploy só com backup no Neon e confirmação do usuário (fora deste plano).

## Review Focus
1. Pessoa sem acréscimo: todos os números (situação, carnê, PIX, lembrete) idênticos aos de antes.
2. Acréscimo registrado no último mês ou depois do fim da caixinha; acréscimo zerado depois de registrado.
3. Desistência com guardado menor que a parte variável; peso 0; responsável com dependentes ativos.
4. Devolução maior que o guardado ou negativa; data inválida.
5. Cartão de entrada tardia só depois do lugar pago e só para quem entrou depois do fechamento.

---

### Task 1: Migração V3 e acréscimo na pessoa

**Files:** Create `src/main/resources/db/migration/V3__acrescimo_da_pessoa.sql`; Modify `pessoa/Pessoa.java` (campos `acrescimo` NUMERIC(12,2), `acrescimoDesde` YearMonth length 7, getters), `pessoa/PessoaService.java`; Test `src/test/java/com/natal/caixinha/pessoa/AcrescimoTest.java` (+ `MigracoesPostgresTest` continua passando).

**Interfaces — Produces:** `Pessoa.getAcrescimo(): BigDecimal` (nulo = não confirmado), `Pessoa.getAcrescimoDesde(): YearMonth`, `Pessoa.entrouTarde(LocalDate fechamentoLista): boolean`; `PessoaService.definirAcrescimo(Long pessoaId, BigDecimal valor)` — valor nulo/negativo → `RegraException("Informe o acréscimo: R$ 0 se o anfitrião não cobrar nada.")`; só para quem entrou tarde → senão `RegraException("O acréscimo é só para quem entrou depois do fechamento da lista.")`; `desde` = mês de hoje limitado a [mesInicio, mesFim] quando passa de nulo/0 para > 0; mantém `desde` ao mudar valor > 0; valor 0 limpa `desde`.

- [ ] Testes: SQL V3 com `ALTER TABLE pessoa ADD COLUMN acrescimo NUMERIC(12,2) CHECK (acrescimo >= 0)` e `ADD COLUMN acrescimo_desde VARCHAR(7)`; pessoa nova com data_entrada 2027-03-01 → `definirAcrescimo(300)` em 15/03/2027 grava 300 e desde 2027-03; mudar para 400 mantém 2027-03; 0 grava 0 e desde nulo; pessoa do seed (entrou 2026-11-16) → RegraException; valor negativo → RegraException. Rodar: FAIL.
- [ ] Implementar. PASS + suíte (inclui migrações no Postgres embutido). Commit "Acréscimo da pessoa: migração V3 e regra de registro".

### Task 2: Valores por mês na CalculadoraCaixinha

**Files:** Modify `caixinha/CalculadoraCaixinha.java`; Test `CalculadoraCaixinhaTest` (novos casos).

**Interfaces — Produces:**
- `static List<BigDecimal> valoresDosMeses(BigDecimal cotaDaPessoa, BigDecimal acrescimo, YearMonth acrescimoDesde, YearMonth mesInicio, YearMonth mesFim)` — um valor por mês da caixinha.
- `static Situacao situacao(List<BigDecimal> valoresDosMeses, BigDecimal guardado, LocalDate hoje, YearMonth mesInicio, YearMonth mesFim)`; a assinatura atual (cota) passa a delegar com lista constante. `Situacao.cota` = valor do mês atual.
- mesesDeAtraso = nº de meses fechados cujo acumulado passa do guardado.

- [ ] Testes: sem acréscimo → 11 × 92; 300 desde 2027-07 → jul..nov 152 (92+60), antes 92; 100 desde 2027-09 → 33,33/33,33/33,34 em set/out/nov; desde depois de nov tratado como nov (o service já limita; aqui desde = nov → tudo em nov); situação em 15/08/2027 com 300 desde jul e guardado = 92×7 → emDia false, mesesDeAtraso 1, paraFicarEmDia = 60 + 152 = 212.00, faltaNoMes 152.00, cota 152.00; todos os casos antigos de `CalculadoraCaixinhaTest` intactos. Rodar: FAIL.
- [ ] Implementar. PASS + suíte. Commit "Calculadora: valor de cada mês com acréscimo dividido".

### Task 3: Carnê, família e resumo usam os valores por mês

**Files:** Modify `caixinha/Carne.java` (`Membro(BigDecimal cota, List<Movimento> movimentos, List<BigDecimal> valores)` + construtor de 2 argumentos = constante), `caixinha/FamiliaService.java`, `caixinha/CaixinhaService.java` (situação com valores da pessoa), `web/PaginaPessoalController.java` + `templates/pessoal/inicio.html` (linha "Inclui R$ X de acréscimo do lugar, dividido até novembro."); Tests: `CarneTest` (caso com valores variáveis), `FamiliaServiceTest` (família com acréscimo), `PaginaPessoalCompletaTest` (linha do acréscimo).

**Interfaces — Consumes:** Task 1 e 2. **Produces:** `Familia.cotaFamilia` = soma dos valores do mês atual; `Familia.acrescimo` (soma dos acréscimos dos pagantes, 0 se nenhum) — novo componente no fim do record.

- [ ] Testes: carnê com valores [92×6, 152×5] e guardado 92×7 → talão 7 PARCIAL com falta 60, talão 8 ABERTO valor 152; pessoa nova (entrou 01/03/2027, peso 1) com acréscimo 300 desde março, em 15/03/2027, sem depósitos → `valorPix` = 92×3 + 300/9 parcela... (usar números exatos calculados no teste a partir de `valoresDosMeses`); página pessoal mostra "Inclui R$ 300,00 de acréscimo do lugar, dividido até novembro."; sem acréscimo os testes atuais passam sem mudança. Rodar: FAIL.
- [ ] Implementar. PASS + suíte. Commit "Carnê, família e resumo com o valor de cada mês".

### Task 4: Entrada tardia no painel

**Files:** Create `web/AcrescimoController.java` (POST `/painel/pessoas/{id}/acrescimo`, param `acrescimo`, redirect para `voltar` = `/painel` ou `/painel/pessoas/{id}`); Modify `web/PainelController.java` (atributo `entradasTardias`: lista de pessoas ativas pagantes que entraram tarde com acréscimo nulo, só quando o lugar está pago — usar `CronogramaService.visao().lugarPago()` ou extrair `lugarPago()` público no CronogramaService), `templates/painel/inicio.html` (cartão), `web/FormularioPessoa.java` + `templates/painel/pessoa-form.html` + `web/PessoasController.java` (campo "Acréscimo cobrado pelo anfitrião", só para quem entrou tarde; salva via `definirAcrescimo` quando preenchido); Test `src/test/java/com/natal/caixinha/web/EntradaTardiaWebTest.java`.

- [ ] Testes: sem aluguel pago → Início sem o cartão; com gasto ALUGUEL ≥ aluguel e uma pessoa que entrou em 2027-03-01 → cartão "Confirme com o anfitrião a lotação da casa" com o nome; POST acréscimo 0 → some; POST "abc" → erro "Valor inválido…" (mensagem de `Dinheiro.ler`); POST para pessoa do seed → erro da regra; formulário da pessoa tardia mostra o campo, o de pessoa do seed não. Rodar: FAIL.
- [ ] Implementar. PASS + suíte. Commit "Painel: acréscimo de quem entrou depois do fechamento".

### Task 5: Desistência

**Files:** Create `pessoa`-independente `caixinha/DesistenciaService.java` (ou pacote `desistencia`), `web/DesistenciaController.java`, `templates/painel/desistencia.html`; Modify `templates/painel/pessoas.html` (botão "Desistência" para ativos); Tests `DesistenciaServiceTest`, `DesistenciaWebTest`.

**Interfaces — Produces:** `record Sugestao(Pessoa pessoa, BigDecimal guardado, boolean lugarPago, boolean assumeVaga, BigDecimal parteVariavel, BigDecimal sugerido)`; `Sugestao sugerir(Long pessoaId, boolean assumeVaga)`; `@Transactional void confirmar(Long pessoaId, BigDecimal valor, LocalDate data)` — valor < 0 ou > guardado → `RegraException("A devolução vai de R$ 0,00 até o que a pessoa guardou (R$ X).")`; pessoa inativa → `RegraException("Fulana já está fora da caixinha.")`; lança DEVOLUCAO "Desistência" se valor > 0 e inativa a pessoa reaproveitando a regra de `PessoaService` (responsável com dependentes ativos → mensagem atual), tudo na mesma transação.
- Parte variável = Σ itens COMIDA, BEBIDA, GELO_CARVAO_DESCARTAVEIS (real ?? previsto) × peso ÷ soma dos pesos ativos (com ela), centavos para baixo, mín(guardado).

- [ ] Testes: lugar não pago, guardou 276 → sugerido 276; lugar pago (gasto ALUGUEL ≥ aluguel), orçamento COMIDA 4400, peso 1, soma 11,5, guardou 500 → parte 382.60, sugerido 382.60; guardou 100 → sugerido 100; `assumeVaga` → 500; confirmar 382.60 → existe DEVOLUCAO 382.60 "Desistência", pessoa inativa, link desativado; confirmar 600 → RegraException e nada gravado; "Adulto 1" (tem dependentes) → RegraException e nada gravado; web: GET mostra "Guardou R$ …" e "Sugestão", POST confirma e redireciona para Pessoas com "Desistência registrada: devolução de R$ 382,60."; sem login → /entrar. Rodar: FAIL.
- [ ] Implementar. PASS + suíte. Commit "Desistência: devolução sugerida e confirmação".

### Task 6: Navegador e README
- [ ] HTML gerado por teste temporário (Início com cartão de entrada tardia; tela de desistência; página pessoal com acréscimo), conferido no `mockup-estatico` a 375px, temas escuro e claro, sem rolagem lateral. Apagar o teste temporário.
- [ ] README: Fase 7 "🟡 parte 1 de 4 pronta (desistência e entrada tardia)". Commit "README: fase 7, parte 1".
