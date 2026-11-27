# Fase 7, parte 2 — Desconto de novembro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** prévia e confirmação do desconto de novembro, aplicado no último mês de cada pessoa.

**Architecture:** `CalculadoraCaixinha` ganha a conta do desconto (pura); `DescontoNovembroService` monta a prévia e grava `desconto_novembro_por_peso`; resumo e família aplicam o desconto ao último valor da lista de meses.

**Tech Stack:** Spring Boot 4.0 (Java 17 APIs), Thymeleaf, JPA, H2 nos testes.

**Spec:** `docs/superpowers/specs/2026-11-27-fase7b-desconto-novembro-design.md`

## Global Constraints
- Build: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; ./mvnw -q test`; contar casos pelos XML do surefire.
- Sem migração. Java 17 APIs. pt-BR neutro. Commits em português com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus
1. Sem desconto (nulo): todos os números idênticos aos de hoje.
2. Pessoa de meia cota, pessoa com acréscimo, pessoa que já adiantou novembro.
3. Sobra negativa ou zero; desconto maior que a cota (novembro nunca negativo).
4. Confirmar/desfazer fora de outubro; sem cota fixada.
5. Inativos: guardado entra na arrecadação, não entram na soma de pesos nem no "falta".

---

### Task 1: Conta do desconto na CalculadoraCaixinha
**Files:** `caixinha/CalculadoraCaixinha.java`; Test `CalculadoraCaixinhaTest` (nova @Nested `DescontoDeNovembro`).
**Produces:** `static BigDecimal descontoPorPeso(BigDecimal sobraPrevista, BigDecimal somaPesos)` (floor inteiro de sobra/2/somaPesos; ≤0 → 0); `static List<BigDecimal> comDesconto(List<BigDecimal> valores, BigDecimal cotaDaPessoa, BigDecimal descontoPorPeso, BigDecimal peso)` (último −= min(cota, d×peso)).
- [ ] Testes: sobra 1150, pesos 11,5 → 50; sobra 1149 → 49 (575/11.5=49.95→49... conferir: 574,5/11,5 = 49,95 → 49); sobra −10 → 0; comDesconto 11×92, d=50, peso 1 → último 42; peso 0,5 (cota 46) → último 21; d=100, peso 1 → último 0 (não negativo); com acréscimo (último 152 = 92+60), d=50 → 102; d nulo/0 → lista igual. FAIL → implementar → PASS. Commit "Calculadora: desconto de novembro".

### Task 2: DescontoNovembroService e aplicação no resumo/família
**Files:** `caixinha/Caixinha.java` (`void definirDescontoNovembro(BigDecimal)`), Create `caixinha/DescontoNovembroService.java`; Modify `CaixinhaService.resumo`, `FamiliaService.daFamilia` (aplicar `comDesconto` com `c.getDescontoNovembroPorPeso()`); Test `DescontoNovembroServiceTest` (RelogioAjustavel).
**Produces:** `record Previa(BigDecimal guardado, BigDecimal rendimentos, BigDecimal doacoes, BigDecimal falta, BigDecimal arrecadacaoPrevista, BigDecimal gastosPrevistos, BigDecimal sobra, BigDecimal descontoPorPeso, BigDecimal reserva, BigDecimal somaPesos, BigDecimal confirmado /*nulo*/, boolean podeConfirmar, String motivo /*nulo se pode*/)`; `Previa previa()`; `void confirmar()`; `void desfazer()`.
- [ ] Testes (orçamento 6000+4400, cota fixada 92, hoje 15/10/2027): sem depósitos → falta = 11×(92×11)+46×11 = 1058×11 = 11638, arrecadação 11638, gastos 10400, sobra 1238, desconto floor(619/11,5)=53, reserva 1238 − 53×11,5 = 628,50; confirmar grava 53 e o novembro de "Adulto 2" vira 39 na família/carnê; adiantado: quem guardou 1100 tem falta 0 e guardado 1100; desfazer volta a nulo; confirmar em 15/09/2027 ou 05/11/2027 → RegraException com as mensagens do design; sem cota fixada → RegraException; sobra ≤ 0 → podeConfirmar false e confirmar recusa. FAIL → implementar → PASS + suíte. Commit "Desconto de novembro: prévia, confirmação e aplicação no último mês".

### Task 3: Tela, Cronograma e página pessoal
**Files:** Create `web/DescontoNovembroController.java`, `templates/painel/desconto-novembro.html`; Modify `templates/painel/cronograma.html` (link na etapa de outubro: etapa cujo mês = mesFim−1), `PaginaPessoalController` + `pessoal/inicio.html` (linha "Novembro com desconto: R$ X (presente do amigo secreto 🎁)" = último talão do carnê, quando desconto > 0); Tests `DescontoNovembroWebTest`, `PaginaPessoalCompletaTest`.
- [ ] Testes: GET mostra "Sobra prevista", "R$ 53" por peso, exemplo meia cota; POST confirmar em outubro → flash "Desconto de novembro confirmado: R$ 53 por cota inteira." e GET mostra "Desfazer"; POST desfazer → flash; fora de outubro → flash erro; cronograma com data mostra link `/painel/desconto-novembro`; página pessoal com desconto mostra a linha; sem login → /entrar. FAIL → implementar → PASS + suíte. Commit "Painel: tela do desconto de novembro".

### Task 4: Navegador e README
- [ ] HTML por teste temporário (tela do desconto; página pessoal), 375px escuro/claro, sem rolagem lateral.
- [ ] README: "🟡 partes 1 e 2 de 4 prontas". Commit.
