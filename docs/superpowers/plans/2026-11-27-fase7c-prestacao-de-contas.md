# Fase 7, parte 3 — Prestação de contas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** partes da sobra por pessoa, escolha da família no link pessoal, tela "Contas" no painel com lançamento das devoluções/créditos, CSV e verificação do saldo R$ 0,00.

**Architecture:** pacote `prestacao`: `Partes` (pura, divisão proporcional com centavos), `EscolhaSobra`/`OpcaoSobra`/`EscolhaSobraRepository`, `PrestacaoService`. Controllers `ContasController` (painel) e rotas novas em `PaginaPessoalController`.

**Tech Stack:** Spring Boot 4.0 (Java 17 APIs), Thymeleaf, JPA, H2 nos testes.

**Spec:** `docs/superpowers/specs/2026-11-27-fase7c-prestacao-de-contas-design.md`

## Global Constraints
- Build: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; ./mvnw -q test`; contar `<testcase>` nos XML.
- Sem migração. Java 17. pt-BR neutro. Commits em português com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Descrições fixas: `"Sobra da festa"` e `"Crédito para " + (ano do mesFim + 1)`.

## Review Focus
1. Soma das partes == sobra exatamente (centavos), inclusive com 3 pessoas e guardados desiguais.
2. Partes não mudam depois de lançar algumas devoluções da sobra.
3. Escolha antes da festa, depois do prazo, por link inválido; família com dependentes grava para todos.
4. Lançar antes do prazo, lançar duas vezes, parte 0.
5. Saldo fecha em R$ 0,00 quando todas as partes são lançadas.

---

### Task 1: Partes (pura)
**Files:** Create `prestacao/Partes.java`; Test `prestacao/PartesTest.java`.
**Produces:** `record Guardado(Long pessoaId, BigDecimal valor)`; `static Map<Long, BigDecimal> dividir(BigDecimal sobra, List<Guardado>)` (LinkedHashMap na ordem recebida; só guardado > 0; sobra ≤ 0 → mapa vazio).
- [ ] Testes: sobra 100, guardados 1/1/1 → 33,34 / 33,33 / 33,33 (resto para o maior; empate → menor id) e soma 100; sobra 1238, guardados 1012 e 506 → proporcional, soma exata; guardado 0 fica de fora; sobra −5 → vazio. FAIL → implementar → PASS. Commit.

### Task 2: Escolha e PrestacaoService
**Files:** Create `prestacao/OpcaoSobra.java` (DEVOLVER, CREDITO), `prestacao/EscolhaSobra.java` (PK pessoa_id, opcao, registrada_em), `prestacao/EscolhaSobraRepository.java`, `prestacao/PrestacaoService.java`; Test `PrestacaoServiceTest` (RelogioAjustavel).
**Produces:**
- `record Linha(Pessoa pessoa, BigDecimal guardado, BigDecimal parte, OpcaoSobra escolha /*nula*/, OpcaoSobra efetiva, boolean lancado)`
- `record Visao(boolean disponivel, LocalDate dataFesta, LocalDate prazo, boolean prazoPassou, BigDecimal entradas, Map<Categoria, BigDecimal> gastosPorCategoria, BigDecimal gastos, BigDecimal devolucoes, BigDecimal sobra, List<Linha> linhas, BigDecimal saldo)`
- `record DaFamilia(BigDecimal parte, OpcaoSobra escolha, boolean podeEscolher)`
- `Visao visao()`, `DaFamilia daFamilia(Long responsavelId)`, `void escolherPelaFamilia(Long responsavelId, OpcaoSobra)` (depois da festa e até o prazo; senão RegraException "A escolha vai até DD/MM/AAAA." / "A prestação de contas abre depois da festa."), `void definirEscolha(Long pessoaId, OpcaoSobra)` (organizador, depois da festa), `void lancar(Long pessoaId)` (depois do prazo; parte > 0; não lançado; DEVOLUCAO com a descrição da opção efetiva; data = hoje), `String csvCreditos()`.
- [ ] Testes (orçamento, cota fixada, data da festa 20/12/2027): antes da festa `disponivel=false`; depois: depósitos de Adulto 2 (1012) e Adulto 3 (506), gasto 1000 → sobra 518, partes 345,33/172,67; família de Adulto 1 escolhe CREDITO → grava para Adulto 1 e Adolescente; depois do prazo escolha → RegraException; lançar antes do prazo → RegraException; depois: lançar Adulto 2 (sem escolha → CREDITO, descrição "Crédito para 2028") → `lancado`, partes iguais às de antes; lançar de novo → RegraException; lançar todos → saldo 0,00; CSV `"Adulto 2;345,33\n…"`. FAIL → implementar → PASS + suíte. Commit.

### Task 3: Telas (painel e página pessoal)
**Files:** Create `web/ContasController.java` (`/painel/contas`, POST `/{id}/escolha`, POST `/{id}/lancar`, GET `/creditos.csv` text/csv;charset=utf-8 attachment), `templates/painel/contas.html`; Modify `fragmentos.html` (menu "Contas"), `PaginaPessoalController` (atributo `prestacao`, `daFamilia`; POST `/c/{token}/sobra` param `opcao` → redirect para a página, flash), `pessoal/inicio.html` (seção); Tests `ContasWebTest`, `PaginaPessoalCompletaTest` (novos casos).
- [ ] Testes: painel antes da festa explica; depois mostra sobra e partes; lançar redireciona com flash; CSV com cabeçalho Content-Disposition; página pessoal depois da festa mostra "A parte da sua família" e os dois botões; POST escolha grava e redireciona; link inválido → 404; sem login no painel → /entrar. FAIL → implementar → PASS + suíte. Commit.

### Task 4: Navegador e README
- [ ] HTML por teste temporário (contas no painel; página pessoal), 375px escuro/claro.
- [ ] README: "🟡 partes 1 a 3 de 4 prontas". Commit.
