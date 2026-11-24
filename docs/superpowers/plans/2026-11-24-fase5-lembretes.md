# Fase 5 — Lembretes e WhatsApp — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** cartões de lembrete no Início do painel (grupo a partir do dia 1, individual a partir do dia 20) com botões de WhatsApp em 1 toque que gravam `AvisoMensal`.

**Architecture:** pacote `lembrete` com a entidade `AvisoMensal` (tabela já existe na V1), um montador de textos puro (`MensagensLembrete`) e um `LembreteService` que decide quais cartões aparecem. Um `LembretesController` recebe o toque (POST), grava o aviso e redireciona para o `wa.me`.

**Tech Stack:** Spring Boot 4.0 (Java 17 APIs), Thymeleaf, JPA, H2 modo PostgreSQL nos testes, JUnit 5 + MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-24-fase5-lembretes-design.md` (e seções 4.3 e 5 da especificação original).

## Global Constraints
- Build: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; ./mvnw -q test`.
- Sem migração nova (tabela `aviso_mensal`: id, mes VARCHAR(7), pessoa_id nulo no GRUPO, tipo 'INDIVIDUAL'|'GRUPO', avisado_em).
- Mensagens em pt-BR, formatação WhatsApp `*negrito*`, gênero neutro, nunca nomes na mensagem do grupo.
- Dinheiro na mensagem com `Dinheiro.formatar` ("R$ 1.012,00"); nomes dos meses com o mesmo formato do painel (`Formato.mes`, minúsculo; capitalizar no começo de frase).
- Link do WhatsApp sempre via `LinkWhatsApp.para(telefone, texto)`.
- Commits pequenos, em português, terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus
1. Mês do cartão fora da caixinha (antes de jan/2027, depois de dez/2027): nenhum cartão, POST recusado.
2. POST com `mes` diferente do cartão visível hoje ou `pessoaId` que não está na lista: recusado com mensagem, nada gravado.
3. Tocar duas vezes: um aviso só.
4. Família só de peso 0 (SEM_VALOR) ou com pagamento parcial: entra/sai da lista corretamente.
5. Responsável sem telefone: link `https://wa.me/?text=`.

---

### Task 1: AvisoMensal (entidade e repositório)

**Files:**
- Create: `src/main/java/com/natal/caixinha/lembrete/TipoAviso.java`, `AvisoMensal.java`, `AvisoMensalRepository.java`
- Test: `src/test/java/com/natal/caixinha/lembrete/AvisoMensalRepositoryTest.java`

**Interfaces:**
- Produces: `enum TipoAviso { INDIVIDUAL, GRUPO }`; `AvisoMensal(YearMonth mes, Pessoa pessoa /*nula no GRUPO*/, TipoAviso tipo, OffsetDateTime avisadoEm)` com getters; `AvisoMensalRepository extends JpaRepository<AvisoMensal, Long>` com `boolean existsByMesAndTipoAndPessoaIsNull(YearMonth, TipoAviso)`, `boolean existsByMesAndTipoAndPessoaId(YearMonth, TipoAviso, Long)`, `List<AvisoMensal> findByMesAndTipo(YearMonth, TipoAviso)`, `void deleteByMesAndTipoAndPessoaId(YearMonth, TipoAviso, Long)`.

- [ ] Teste (`@SpringBootTest(properties = BancoDeTeste.URL...)`, `@Transactional`): grava um GRUPO de 2027-03 e um INDIVIDUAL de 2027-03 para "Adulto 1"; `existsByMesAndTipoAndPessoaIsNull(2027-03, GRUPO)` true, para 2027-04 false; `existsByMesAndTipoAndPessoaId(...)` true; depois de `deleteBy...` false. Rodar: FAIL (classes não existem).
- [ ] Implementar (mes com `@Column(length = 7)`, o `ConversorMes` existente aplica; tipo `@Enumerated(STRING)`; `pessoa` `@ManyToOne(fetch = LAZY)` nullable). Rodar: PASS.
- [ ] Commit "Aviso mensal: entidade e repositório".

### Task 2: MensagensLembrete (textos puros)

**Files:**
- Create: `src/main/java/com/natal/caixinha/lembrete/MensagensLembrete.java`
- Test: `src/test/java/com/natal/caixinha/lembrete/MensagensLembreteTest.java`

**Interfaces:**
- Produces:
  - `record Grupo(YearMonth mesFechado /*nulo no 1º mês*/, YearMonth mesNovo /*nulo no mês após o último*/, BigDecimal arrecadado, BigDecimal meta, int percentual, int emDia, int pagantes, BigDecimal cota)`
  - `record Individual(String nomeResponsavel, YearMonth mes, BigDecimal cotaFamilia, BigDecimal valorParaFicarEmDia, int maiorAtraso, String link)`
  - `static String grupo(Grupo)`, `static String individual(Individual)`

- [ ] Testes com os textos exatos (quebras `\n`):
  - meio: `grupo(new Grupo(2027-01, 2027-02, 1012.00, 11440.00, 9, 10, 11, 92.00))` ==
    `"*Caixinha da festa* 🎉\nFechamos *janeiro*: R$ 1.012,00 de R$ 11.440,00 (9%).\n*10 de 11* pagantes estão em dia 💪\n*Fevereiro* começou: a cota é R$ 92,00 por pessoa. O valor da sua família e o PIX estão no seu link pessoal."`
  - 1º mês (mesFechado nulo, mesNovo 2027-01): `"*Caixinha da festa* 🎉\nA caixinha começou! *Janeiro* é o primeiro mês: a cota é R$ 92,00 por pessoa. O valor da sua família e o PIX estão no seu link pessoal."`
  - último (mesFechado 2027-11, mesNovo nulo): linhas "Fechamos *novembro*: …", "*X de Y* pagantes…", "Último depósito feito. Agora é festa! 🥳"
  - cota zero: nenhuma frase "a cota é" (meio: termina em "*Fevereiro* começou."; 1º mês: "A caixinha começou! *Janeiro* é o primeiro mês.")
  - individual simples (`"Ana Souza"`, 2027-03, 138.00, 138.00, 0, `"https://x/c/abc"`) == `"Oi, Ana! 😊 Lembrete da caixinha da festa: a cota de *março* da família é *R$ 138,00*.\nO PIX já está pronto no seu link: https://x/c/abc"`
  - maiorAtraso 2, valorParaFicarEmDia 414.00: mais `"\nPara ficar em dia: *R$ 414,00*. Se precisar combinar algo, é só me chamar."`; maiorAtraso 1 não acrescenta.
  - Rodar: FAIL.
- [ ] Implementar (nome do mês: `mes.getMonth().getDisplayName(TextStyle.FULL, Locale.of("pt","BR"))`; capitalizar quando no início da frase em negrito; primeiro nome = até o 1º espaço). Rodar: PASS.
- [ ] Commit "Textos das mensagens de lembrete".

### Task 3: LembreteService (quando cada cartão aparece)

**Files:**
- Create: `src/main/java/com/natal/caixinha/lembrete/LembreteService.java`
- Create (teste): `src/test/java/com/natal/caixinha/RelogioAjustavel.java` (`Clock` mutável `@TestConfiguration` com `@Primary`, método `para(LocalDate dia)` que fixa 10h em `CaixinhaApplication.FUSO`)
- Test: `src/test/java/com/natal/caixinha/lembrete/LembreteServiceTest.java`

**Interfaces:**
- Consumes: Task 1 e 2; `CaixinhaService.resumo()` (arrecadado, cota.meta, percentual, emDia, pagantes, cota.cota); `FamiliaService.daFamilia(id)` (estado, cotaFamilia, valorPix, pagantes[].situacao.mesesDeAtraso); `LinkService.tokensAtivos()`; `PessoaRepository.findByAtivoTrueOrderByNomeAsc()`.
- Produces:
  - `record CartaoGrupo(YearMonth mes, String texto)`
  - `record LinhaIndividual(Long pessoaId, String nome, BigDecimal valor, String telefone, String texto, boolean avisado)`
  - `record CartaoIndividual(YearMonth mes, List<LinhaIndividual> linhas)`
  - `Optional<CartaoGrupo> cartaoGrupo(String urlBase)`, `Optional<CartaoIndividual> cartaoIndividual(String urlBase)` (urlBase para montar `urlBase + "/c/" + token`)
  - `@Transactional void avisarGrupo(YearMonth mes)`, `@Transactional LinhaIndividual avisarIndividual(YearMonth mes, Long pessoaId, String urlBase)`, `@Transactional void desfazer(YearMonth mes, Long pessoaId)` — todos lançam `RegraException("Este lembrete não está mais disponível.")` se o cartão daquele mês não está visível hoje ou a pessoa não está na lista; gravar é idempotente.
- Regras: grupo visível se `mesInicio ≤ mês(hoje) ≤ mesFim+1` e sem aviso GRUPO no mês; `mesFechado` = mês anterior se ≥ mesInicio, `mesNovo` = mês de hoje se ≤ mesFim. Individual visível se `mesInicio ≤ mês(hoje) ≤ mesFim`, dia ≥ 20, lista = pessoas ativas sem responsável cuja família tem estado PENDENTE ou ATRASADO (ordenadas por nome), e alguma linha sem aviso. `valor` = `valorPix`; `maiorAtraso` = máximo de `mesesDeAtraso` dos pagantes.

- [ ] Testes (`@SpringBootTest`, `@Import(RelogioAjustavel.class)`, `@Transactional`, seed + orçamento como nos testes da Fase 4):
  - 24/11/2026 → sem cartões; 01/01/2027 → grupo com "A caixinha começou"; 15/03/2027 → grupo com "Fechamos *fevereiro*" e sem individual; 01/12/2027 → grupo com "Agora é festa"; 01/01/2028 → nada.
  - depois de `avisarGrupo(2027-03)` o grupo some; chamar duas vezes deixa 1 aviso; `avisarGrupo(2027-04)` em 15/03 → RegraException.
  - 22/03/2027 sem depósitos → individual lista os responsáveis (12? conferir: todos os responsáveis pagantes do seed); depósito que paga a família de "Adulto 2" → ela sai; família só com peso 0 não aparece.
  - `avisarIndividual` marca `avisado=true`; quando todos avisados, `cartaoIndividual` vazio; `desfazer` faz voltar; `avisarIndividual` de pessoa fora da lista → RegraException.
  - texto individual contém o link `urlBase + "/c/" + token` e, com 2 meses de atraso (em 22/03 sem depósito: jan e fev fechados = 2), a linha "Para ficar em dia".
  - Rodar: FAIL.
- [ ] Implementar. Rodar: PASS e suíte inteira verde.
- [ ] Commit "Lembretes: regras dos cartões do grupo e individual".

### Task 4: Painel — cartões e botões

**Files:**
- Create: `src/main/java/com/natal/caixinha/web/LembretesController.java`
- Modify: `web/PainelController.java` (atributos `cartaoGrupo`, `cartaoIndividual`), `templates/painel/inicio.html` (dois cartões logo depois dos avisos), `static/js/painel.js` (forms `data-recarregar` recarregam a aba ~1 s depois do submit), `static/css/estilo.css` se precisar
- Test: `src/test/java/com/natal/caixinha/web/LembretesWebTest.java`

**Interfaces:**
- Consumes: Task 3.
- Produces: `POST /painel/lembretes/grupo` (`mes`) → grava e `redirect:` para `LinkWhatsApp.para(null, texto)`; `POST /painel/lembretes/individual` (`mes`, `pessoaId`) → grava e redirect para `LinkWhatsApp.para(telefone, texto)`; `POST /painel/lembretes/desfazer` (`mes`, `pessoaId`) → redirect `/painel`. RegraException → flash `erro` e redirect `/painel`. urlBase como em `PessoasController.urlBase()` (extrair para `Formato` ou helper estático compartilhado).
- Template: cartão grupo com `<pre class="mensagem">` do texto, botão "Copiar" (`data-copiar`) e form `target="_blank" data-recarregar` "Enviar no WhatsApp". Cartão individual: `<ul>` com nome, valor, e form "Enviar no WhatsApp" ou "Avisado ✓" + form "desfazer".

- [ ] Testes (MockMvc, `@WithMockUser(roles="ORGANIZADOR")`, `RelogioAjustavel`): GET /painel em 15/03 mostra "Lembrete de março" e o texto; POST grupo com csrf → 3xx com `Location` começando por `https://wa.me/?text=` e o cartão some; em 22/03 POST individual de "Adulto 2" (com telefone) → `Location` começa com `https://wa.me/55`; GET mostra "Avisado ✓"; desfazer volta; POST sem login → redirect /entrar; POST com mês errado → redirect /painel com erro e nada gravado. Rodar: FAIL.
- [ ] Implementar. Rodar: PASS, suíte verde.
- [ ] Commit "Painel: cartões de lembrete com WhatsApp em 1 toque".

### Task 5: Verificação no navegador e README

- [ ] Preview `caixinha-demo` (data real 24/11/2026 → sem cartões; conferir que o Início não quebra). Para ver os cartões, rodar a demo com um relógio adiantado só se existir propriedade para isso; senão confiar nos testes web e conferir o HTML do teste.
- [ ] README: Fase 5 "🟡 pronta; falta usar no dia 1º de janeiro".
- [ ] Commit "README: fase 5 pronta".
