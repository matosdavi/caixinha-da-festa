# Fase 6 — Cronograma — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** tela Cronograma com data da festa, etapas recalculadas, verificação do pagamento do lugar (100% do aluguel) e "próxima etapa" na página pessoal e na mensagem do grupo.

**Architecture:** pacote `cronograma` com uma classe pura `Cronograma` (etapas, próxima etapa, verificação) e um `CronogramaService` (lê configuração, orçamento, resumo e relógio; grava a data da festa). Controller `CronogramaController` em `/painel/cronograma`.

**Tech Stack:** Spring Boot 4.0 (Java 17 APIs), Thymeleaf, JPA, H2 nos testes, JUnit 5 + MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-24-fase6-cronograma-design.md`

## Global Constraints
- Build: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; ./mvnw -q test`. Só APIs do Java 17 (nada de `Locale.of`).
- Sem migração (`caixinha.data_festa` já existe).
- Textos em pt-BR, gênero neutro; dinheiro com `Dinheiro.formatar`; meses minúsculos ("maio").
- Commits pequenos em português com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus
1. Data da festa 20/12 vs 19/12 vs 01/01/2028: meses de procurar/reservar corretos.
2. Data fora de dez/2027–jan/2028, texto inválido, ou campo vazio (limpa a data).
3. Aluguel com valor real diferente do previsto; sem item ALUGUEL.
4. Próxima etapa quando todas as etapas estão feitas ou sem data depois de janeiro.
5. Mensagem do grupo continua igual quando não há próxima etapa.

---

### Task 1: Cronograma (classe pura)

**Files:** Create `src/main/java/com/natal/caixinha/cronograma/Cronograma.java`; Test `src/test/java/com/natal/caixinha/cronograma/CronogramaTest.java`

**Interfaces — Produces:**
- `enum Estado { FEITO, AGORA, PROXIMA }`
- `record Etapa(String titulo, String detalhe, YearMonth mes, Estado estado, boolean reserva)` com `String frase()` = título com a 1ª letra minúscula.
- `record Dados(LocalDate dataFesta /*nula*/, YearMonth mesInicio, YearMonth mesFim, LocalDate fechamentoLista, LocalDate hoje)`
- `static List<Etapa> etapas(Dados)`; `static Optional<Etapa> proxima(List<Etapa>)` (primeira não FEITO)
- `static int antecedenciaProcurar(LocalDate festa)` → 7 se (dez e dia ≥ 20) ou janeiro, senão 6 (reservar = procurar − 1)
- `record Verificacao(YearMonth mes, BigDecimal previsto, BigDecimal aluguel, BigDecimal falta /*0 se cobre*/)`
- `static Verificacao verificar(YearMonth mesInicio, YearMonth mesReserva, BigDecimal arrecadacaoMensal, BigDecimal extras /*rendimentos+doações−gastos*/, BigDecimal aluguel)`

- [ ] Testes: sem data → 2 etapas (títulos do design); festa 20/12/2027 → procurar maio/2027, reservar junho/2027 (`reserva=true`), outubro, novembro, festa dezembro (7 etapas); festa 19/12/2027 → junho/julho; festa 01/01/2028 → junho/julho (7 e 6 meses antes de jan/2028); estados em 15/03/2027 (dez/2026 e jan FEITO; procurar PROXIMA); em maio procurar AGORA e `proxima` = procurar; tudo feito (hoje fev/2028) → `proxima` vazio; `frase()` de "Procurar o lugar" = "procurar o lugar"; `verificar(jan, mai, 1058, 0, 6000)` → previsto 5290.00, falta 710.00; `verificar(jan, jun, 1058, 0, 6000)` → previsto 6348.00, falta 0.00; extras negativos reduzem o previsto. Rodar: FAIL.
- [ ] Implementar. Rodar: PASS. Commit "Cronograma: etapas, próxima etapa e verificação do pagamento do lugar".

### Task 2: CronogramaService e data da festa

**Files:** Modify `caixinha/Caixinha.java` (`void definirDataFesta(LocalDate)` package-private ou público ao pacote via service), Create `src/main/java/com/natal/caixinha/cronograma/CronogramaService.java`; Test `src/test/java/com/natal/caixinha/cronograma/CronogramaServiceTest.java`

**Interfaces — Produces:**
- `record Visao(LocalDate dataFesta, List<Cronograma.Etapa> etapas, Optional<Cronograma.Verificacao> verificacao /*vazia sem data*/, boolean semAluguel)`
- `Visao visao()`, `Optional<Cronograma.Etapa> proximaEtapa()`, `@Transactional void definirDataFesta(LocalDate /*nula limpa*/)` — fora de [1º dia após mesFim, último dia de mesFim+2] lança `RegraException("A festa precisa ser depois do último depósito, em dezembro de 2027 ou em janeiro de 2028.")` (meses montados a partir de mesFim, não fixos).
- `Caixinha.definirDataFesta` fica no pacote `caixinha`; o service chama via um método público novo `CaixinhaService.definirDataFesta(LocalDate)` que valida e grava.
- Aluguel = Σ itens ALUGUEL (valorReal ?? valorPrevisto); `semAluguel` = aluguel 0. extras = rendimentos + doações − gastos do `resumo()`.

- [ ] Testes (`@SpringBootTest`, `RelogioDeTeste`, orçamento da spec 6000+4400): sem data → 2 etapas, verificação vazia; definir 20/12/2027 → 7 etapas e verificação maio... (reserva junho: previsto 6348.00, falta 0); item ALUGUEL com real 6500 → falta 152.00 (6348 vs 6500); data 30/11/2027 e 01/02/2028 → RegraException; null limpa; sem item ALUGUEL (só COMIDA) → `semAluguel`. Rodar: FAIL.
- [ ] Implementar. PASS + suíte. Commit "Cronograma: data da festa e visão do cronograma".

### Task 3: Tela Cronograma no painel

**Files:** Create `web/CronogramaController.java`, `templates/painel/cronograma.html`; Modify `templates/fragmentos.html` (item "Cronograma" entre Orçamento e Configurações); Test `src/test/java/com/natal/caixinha/web/CronogramaWebTest.java`

**Interfaces:** GET `/painel/cronograma` (model `visao`); POST `/painel/cronograma` param `dataFesta` (ISO `yyyy-MM-dd` do `<input type=date>`, vazio limpa) → flash "Data da festa salva." / "Data da festa apagada: em votação." e redirect; erro → flash `erro`; data inválida → "Data inválida.".

- [ ] Testes: GET sem data mostra "Data em votação" e "Fechar a lista e fixar a cota"; POST 2027-12-20 → redirect e GET mostra "Procurar o lugar", "maio", "Dá para pagar"; com aluguel real 6500 mostra "faltam R$ 152,00" e "pague uma parte agora"; POST 2027-11-30 → flash erro; POST vazio volta para "Data em votação"; sem login → /entrar. Rodar: FAIL.
- [ ] Implementar (estados com ✅ / 👉 e texto, não só cor). PASS + suíte. Commit "Painel: tela Cronograma".

### Task 4: Próxima etapa na página pessoal e na mensagem do grupo

**Files:** Modify `lembrete/MensagensLembrete.java` (`Grupo` ganha `String proximaEtapa /*frase, nula*/` e `YearMonth mesProximaEtapa`), `lembrete/LembreteService.java` (preenche com `CronogramaService.proximaEtapa()`), `web/PaginaPessoalController.java` + `templates/pessoal/inicio.html` (linha "Próxima etapa: procurar o lugar, em maio."); Tests: atualizar `MensagensLembreteTest` (construtor), novo caso com próxima etapa; `PaginaPessoalCompletaTest` novo caso.

- [ ] Testes: grupo com próxima etapa termina com `"\n📅 Próxima etapa: *procurar o lugar* (maio)"`; sem próxima etapa, texto idêntico ao de hoje; página pessoal com data 20/12/2027 em 15/03/2027 mostra "Próxima etapa: procurar o lugar, em maio."; sem data em 15/03 → sem a linha. Rodar: FAIL.
- [ ] Implementar. PASS + suíte. Commit "Próxima etapa na página pessoal e na mensagem do grupo".

### Task 5: Navegador e README
- [ ] Gerar o HTML do cronograma (data 20/12/2027, aluguel real 6500) por teste temporário, abrir no `mockup-estatico` a 375px nos temas escuro e claro; conferir sem rolagem lateral. Apagar o teste temporário.
- [ ] README: Fase 6 "🟡 pronta; falta definir a data da festa". Commit "README: fase 6 pronta".
