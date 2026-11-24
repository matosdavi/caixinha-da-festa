# Fase 4: página pessoal, PIX, .ics e identidade visual. Plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar o progresso.

**Objetivo:** entregar a página `/c/{token}` completa, em forma de carnê, com o PIX copia e cola da família, o QR Code e o lembrete `.ics`. Junto vêm a tela de Configurações do PIX, o atalho "Pagamento da família" e a identidade visual nova (escura por padrão, clara quando o celular está no claro).

**Arquitetura:** regras puras e testáveis em classes estáticas. São elas: `pix/Crc16`, `pix/BrCode`, `pix/ChavePix`, `pix/QrCodeSvg`, `agenda/LembreteIcs`, `caixinha/Carne` e `caixinha/DivisaoFamilia`. Um serviço `caixinha/FamiliaService` junta pessoa, cota e lançamentos e entrega tudo pronto para a página. Os controllers só montam o modelo, e os templates Thymeleaf seguem o esboço aprovado.

**Tecnologia:** Spring Boot 4.0, Java 17, Thymeleaf, JPA, H2 (testes), ZXing core (QR) e Archivo variável servida pelo próprio app.

**Spec:** `docs/superpowers/specs/2026-11-17-fase4-pagina-pessoal-design.md`, com o esboço aprovado em `docs/superpowers/specs/2026-11-17-fase4-mockup.html`. Leia os dois antes de começar.

## Restrições globais

- Use só APIs do Java 17: nada de `Locale.of`, `List.getFirst` nem `String.formatted` com padrões novos. O locale é `Locale.forLanguageTag("pt-BR")`.
- Build e testes no Git Bash:
  ```bash
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
  ./mvnw -q test
  ```
  Para classes com `@Nested`, filtre com `'-Dtest=Classe*'`.
- **Nenhuma migração nova:** as colunas `chave_pix`, `nome_recebedor` e `cidade_recebedor` já existem desde a V1. Nunca edite a V1 nem a V2.
- Dinheiro sempre em `BigDecimal` com escala 2. O saldo nunca é gravado.
- Testes com data usam `@Import(RelogioDeTeste.class)`, que fixa o relógio em 15/03/2027 às 10h (mês atual = março, 2 meses fechados).
- Nada em `/c/**` pode buscar recurso em outro domínio: sem CDN, sem Google Fonts. Toda resposta de `/c/**` leva `Cache-Control: no-store`.
- Texto da página pessoal: nunca menor que 16px (`rem` sobre `html { font-size: 106.25% }`), contraste AA nos dois temas, alvos de toque com 48px ou mais, sem vermelho e sem tom de cobrança.
- Use uma palavra só por conceito na tela: "mês" e "cota", nunca "parcela". A sigla ".ics" não aparece na tela.
- Faça commits pequenos, um por tarefa, com mensagem em português terminando em `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. **Nada de push nem deploy** sem o usuário confirmar.
- Antes de baixar qualquer arquivo (a fonte da Tarefa 10), peça autorização ao usuário informando nome, origem e tamanho.

## Pontos de atenção na revisão

1. **Nome ou cidade com acento, minúscula ou longo demais.** O PIX precisa sair sem acento, em maiúsculas e cortado em 25 e 15 caracteres; o banco recusa outra coisa. O teste fica na Tarefa 1.
2. **Valor grande ou quebrado** (1234,5). O campo 54 tem que sair `1234.50`, sem separador de milhar e com o tamanho certo. O teste fica na Tarefa 1.
3. **Responsável com peso 0** (avó que paga pelos netos). A página funciona, e a sobra de centavos da divisão vai para o primeiro pagante. Os testes ficam nas Tarefas 6 e 7.
4. **Devolução depois de meses carimbados.** O talão volta a ficar sem carimbo. O teste fica na Tarefa 5.
5. **Duas famílias no banco.** A página de uma nunca mostra nome, valor ou extrato da outra. O teste fica na Tarefa 11.

---

### Tarefa 1: CRC16 e gerador do BR Code

**Arquivos:**
- Criar: `src/main/java/com/natal/caixinha/pix/Crc16.java`
- Criar: `src/main/java/com/natal/caixinha/pix/BrCode.java`
- Teste: `src/test/java/com/natal/caixinha/pix/BrCodeTest.java`

**Interfaces que esta tarefa entrega:**
- `Crc16.ccittFalse(String texto): String`: polinômio 0x1021, valor inicial 0xFFFF, bytes em UTF-8, 4 dígitos hexadecimais maiúsculos.
- `record BrCode.Recebedor(String chave, String nome, String cidade)`
- `BrCode.gerar(Recebedor r, BigDecimal valor, String txid): String`, com campos na ordem 00, 26, 52, 53, 54 (omitido se zero), 58, 59, 60, 62, 63.
- `BrCode.limpar(String texto, int maximo): String`: remove acentos (NFD sem marcas), põe em maiúsculas, mantém só `A-Z`, `0-9` e espaço, junta espaços repetidos, faz `trim` e corta no máximo.
- `BrCode.txid(long responsavelId): String` = `"CX2027" + responsavelId`.

- [ ] **Passo 1: escrever os testes que falham**

```java
@Test void crcDoVetorPadrao() { assertEquals("29B1", Crc16.ccittFalse("123456789")); }

@Test void crcDoExemploDoBancoCentral() {
    String semCrc = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
            + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304";
    assertEquals("1D3D", Crc16.ccittFalse(semCrc));
}

@Test void semValorOmiteOCampo54() {
    String pix = BrCode.gerar(new BrCode.Recebedor("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", "Brasília"),
            BigDecimal.ZERO, "***");
    assertTrue(pix.startsWith("00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
            + "52040000530398658"));
    assertFalse(pix.contains("5303986" + "54"));
    assertTrue(pix.contains("5802BR5913FULANO DE TAL6008BRASILIA62070503***6304"));
    assertEquals(Crc16.ccittFalse(pix.substring(0, pix.length() - 4)), pix.substring(pix.length() - 4));
}

@Test void valorComPontoEDuasCasas() {
    String pix = BrCode.gerar(recebedor(), new BigDecimal("138"), "CX20277");
    assertTrue(pix.contains("5406138.00"));
    assertTrue(pix.contains("62110507CX20277"));
}

@Test void valorGrandeSemSeparadorDeMilhar() {
    assertTrue(BrCode.gerar(recebedor(), new BigDecimal("1234.5"), "***").contains("54071234.50"));
}

@Test void nomeECidadeLimposECortados() {
    assertEquals("JOAO DA CONCEICAO DE ARAU", BrCode.limpar("João  da Conceição de Araújo Filho", 25));
    assertEquals("SAO JOSE DOS CA", BrCode.limpar("São José dos Campos", 15));
    assertEquals("ANA", BrCode.limpar("  Ana!  ", 25));
}
```

- [ ] **Passo 2: rodar e ver falhar** com `./mvnw -q test '-Dtest=BrCodeTest'`. O esperado é um erro de compilação, porque `Crc16` e `BrCode` ainda não existem.
- [ ] **Passo 3: implementar** `Crc16` e `BrCode`. Cada campo é `id + tamanho com 2 dígitos + valor`. O campo 26 tem os subcampos `00 = br.gov.bcb.pix` e `01 = chave`; o campo 62 tem o subcampo `05 = txid`. O CRC é calculado sobre todo o texto já terminado em `6304`.
- [ ] **Passo 4: rodar e ver passar.** Todos os testes da classe devem passar.
- [ ] **Passo 5: commitar** com a mensagem "PIX: gerador do BR Code com CRC16 (testes)".

### Tarefa 2: validar e normalizar a chave PIX

**Arquivos:**
- Criar: `src/main/java/com/natal/caixinha/pix/ChavePix.java`
- Teste: `src/test/java/com/natal/caixinha/pix/ChavePixTest.java`

**Interface:** `enum ChavePix.Tipo { CPF("CPF"), CNPJ("CNPJ"), CELULAR("Celular"), EMAIL("E-mail"), ALEATORIA("Chave aleatória") }`, com o método `getRotulo()`. Também `ChavePix.normalizar(Tipo tipo, String texto): String`, que lança `RegraException` com mensagem clara em português.

| Tipo | Entrada | Saída |
|---|---|---|
| CPF | `529.982.247-25` | `52998224725` (dígitos verificadores válidos) |
| CPF | `529.982.247-24` | erro "CPF inválido: confira os números." |
| CNPJ | `11.222.333/0001-81` | `11222333000181` |
| CELULAR | `(11) 99999-8888` | `+5511999998888` |
| CELULAR | `+55 11 99999-8888` | `+5511999998888` |
| CELULAR | `9999-8888` | erro (falta o DDD; o resultado precisa ter `+55` + 10 ou 11 dígitos) |
| EMAIL | ` Fulano@Exemplo.com ` | `fulano@exemplo.com` (precisa ter `@` e ponto depois dele; no máximo 77 caracteres) |
| ALEATORIA | `123E4567-E12B-12D1-A456-426655440000` | em minúsculas, no formato UUID 8-4-4-4-12 |
| qualquer | vazio | erro "Informe a chave PIX." |

- [ ] Passo 1: escrever os testes da tabela, um `@ParameterizedTest` para os casos válidos e outro para os inválidos.
- [ ] Passo 2: rodar `'-Dtest=ChavePixTest'` e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar e ver passar.
- [ ] Passo 5: commitar com a mensagem "PIX: validação da chave por tipo".

### Tarefa 3: QR Code em SVG

**Arquivos:**
- Modificar: `pom.xml`, adicionando `com.google.zxing:core:3.5.3`, só o core.
- Criar: `src/main/java/com/natal/caixinha/pix/QrCodeSvg.java`
- Teste: `src/test/java/com/natal/caixinha/pix/QrCodeSvgTest.java`

**Interface:** `QrCodeSvg.svg(String texto): String` devolve `<svg xmlns=... viewBox="0 0 N N" role="img" aria-label="QR Code do PIX">`, com um `<rect>` branco de fundo e um único `<path>` escuro (`#000`) formado por um quadrado `M x y h1 v1 h-1 z` por módulo ligado. Use `QRCodeWriter` com `ErrorCorrectionLevel.M` e `EncodeHintType.MARGIN = 4`.

- [ ] Passo 1: escrever os testes. Para `svg("teste")`, o resultado começa com `<svg`, contém `viewBox="0 0 29 29"` (21 módulos + 2×4 de margem), contém `fill="#fff"` e exatamente um `<path`.
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar e ver passar.
- [ ] Passo 5: commitar com a mensagem "PIX: QR Code em SVG gerado no servidor".

### Tarefa 4: arquivo de lembrete `.ics`

**Arquivos:**
- Criar: `src/main/java/com/natal/caixinha/agenda/LembreteIcs.java`
- Teste: `src/test/java/com/natal/caixinha/agenda/LembreteIcsTest.java`

**Interfaces:**
- `record LembreteIcs.Dados(long pessoaId, int ano, YearMonth inicio, int meses, String resumo, String link, Instant agora)`
- `LembreteIcs.gerar(Dados d): String`: linhas terminadas em CRLF, dobradas em 75 octetos (sem partir um caractere UTF-8), com escape de `\`, `;`, `,` e quebra de linha em SUMMARY e DESCRIPTION.

O texto gerado tem, nesta ordem:
- `BEGIN:VCALENDAR`, `VERSION:2.0`, `PRODID:-//Caixinha da Festa//PT-BR`, `CALSCALE:GREGORIAN`, `BEGIN:VEVENT`;
- `UID:caixinha-{ano}-pessoa-{id}@caixinha-da-festa`, `DTSTAMP:{agora em UTC, yyyyMMdd'T'HHmmss'Z'}`;
- `DTSTART;VALUE=DATE:{inicio}01`, `DTEND;VALUE=DATE:{dia seguinte}`, `RRULE:FREQ=MONTHLY;COUNT={meses}`;
- `SUMMARY:{resumo}`, `DESCRIPTION:{link}`;
- `BEGIN:VALARM`, `ACTION:DISPLAY`, `DESCRIPTION:{resumo}`, `TRIGGER;RELATED=START:PT9H`, `END:VALARM`;
- `END:VEVENT`, `END:VCALENDAR`.

- [ ] Passo 1: escrever os testes:
  - `new Dados(7, 2027, YearMonth.of(2027,1), 11, "Caixinha da festa — depositar R$ 138,00", "https://x/c/abc", Instant.parse("2027-03-15T13:00:00Z"))` contém `DTSTART;VALUE=DATE:20270101\r\n`, `RRULE:FREQ=MONTHLY;COUNT=11\r\n`, `UID:caixinha-2027-pessoa-7@caixinha-da-festa\r\n` e `DTSTAMP:20270315T130000Z\r\n`;
  - com início em `2027-04` e 8 meses, sai `DTSTART;VALUE=DATE:20270401` e `COUNT=8`;
  - um resumo com `,` e `;` sai escapado como `\,` e `\;`;
  - com um link de 120 caracteres, nenhuma linha passa de 75 bytes, e a linha seguinte começa com espaço;
  - um resumo com "—" perto do limite não parte o caractere: ao desdobrar, o texto volta igual ao original.
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar e ver passar.
- [ ] Passo 5: commitar com a mensagem "Agenda: gerador do lembrete .ics (testes)".

### Tarefa 5: regra do carnê

**Arquivos:**
- Criar: `src/main/java/com/natal/caixinha/caixinha/Carne.java`
- Teste: `src/test/java/com/natal/caixinha/caixinha/CarneTest.java`

**Interfaces:**
- `record Carne.Movimento(LocalDate data, BigDecimal valor)`: o valor já vem com sinal (devolução é negativa), e a lista chega em ordem de data e id.
- `record Carne.Membro(BigDecimal cota, List<Movimento> movimentos)`: só pagantes, com cota maior que zero.
- `enum Carne.Estado { PAGO, PARCIAL, ABERTO }`
- `record Carne.Talao(YearMonth mes, int numero, BigDecimal valor, Estado estado, BigDecimal falta, LocalDate pagoEm)`
- `Carne.montar(List<Membro> membros, YearMonth inicio, YearMonth fim): List<Talao>`

Regras, para o talão *k* (1 até o total de meses), com `g` = soma dos movimentos de cada membro:
- o valor do talão é a soma das cotas;
- a falta é a soma, por membro, de `min(cota, max(0, k×cota − g))`;
- o estado é PAGO se a falta for 0, ABERTO se a falta for igual ao valor do talão, e PARCIAL nos outros casos;
- `pagoEm` só existe quando o talão está PAGO. Para cada membro, percorra a soma corrente e guarde a data do último movimento em que ela passou de `< k×cota` para `≥ k×cota`. O `pagoEm` é a maior dessas datas entre os membros;
- membros vazios, ou todas as cotas zero, fazem todos os talões saírem ABERTO, com falta 0 e valor 0.

- [ ] Passo 1: escrever os testes. Em todos, Ana tem cota 92, Theo tem cota 46, e os meses vão de 2027-01 a 2027-11.
  - `doisMesesPagosPelaFamilia`: Ana com +92 em 08/01 e +92 em 11/02, Theo com +46 em 08/01 e +46 em 11/02. Talões 1 e 2 PAGO, com `pagoEm` 08/01 e 11/02; talão 3 ABERTO com falta 138; 11 talões no total; `valor` 138.
  - `parcialQuandoSoUmPagou`: Ana +92 em 08/01, Theo sem nada. Talão 1 PARCIAL com falta 46.
  - `carimboEsperaOUltimoDaFamilia`: Ana +92 em 05/01, Theo +46 em 20/01. `pagoEm` do talão 1 é 20/01.
  - `adiantamentoEmDezembro`: Ana +184 em 10/12/2026, Theo +92 em 10/12/2026. Talões 1 e 2 PAGO com `pagoEm` 10/12/2026.
  - `devolucaoTiraOCarimbo`: Ana +92 e +92, depois −92 em 01/03; Theo +46 e +46. Talão 2 PARCIAL com falta 92.
  - `devolucaoEDepoisNovoDeposito`: o caso anterior mais Ana +92 em 10/03. Talão 2 PAGO com `pagoEm` 10/03.
  - `semPagantes`: lista vazia. 11 talões ABERTO, com valor 0 e falta 0.
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar e ver passar.
- [ ] Passo 5: commitar com a mensagem "Carnê: talões por mês com carimbo e data (testes)".

### Tarefa 6: divisão do pagamento da família

**Arquivos:**
- Criar: `src/main/java/com/natal/caixinha/caixinha/DivisaoFamilia.java`
- Teste: `src/test/java/com/natal/caixinha/caixinha/DivisaoFamiliaTest.java`

**Interfaces:**
- `record DivisaoFamilia.Membro(Long pessoaId, BigDecimal peso, BigDecimal paraFicarEmDia, boolean responsavel)`: só pagantes, na ordem em que aparecem na tela (responsável primeiro).
- `record DivisaoFamilia.Parte(Long pessoaId, BigDecimal valor)`
- `DivisaoFamilia.dividir(BigDecimal total, List<Membro> membros): List<Parte>`, devolvendo as partes na mesma ordem dos membros e somando exatamente o total.

Regra: seja `s` a soma de `paraFicarEmDia`.
- Se `total ≤ s` e `s > 0`, cada parte é `floor2(total × falta_i ÷ s)`.
- Senão, cada parte é `falta_i + floor2((total − s) × peso_i ÷ Σpeso)`.
- O resto em centavos vai para o membro responsável, ou para o primeiro da lista se o responsável não for pagante.

- [ ] Passo 1: escrever os testes, com Ana (peso 1, responsável) e Theo (peso 0,5):
  - total exato 138, faltas 92 e 46: partes 92 e 46;
  - parcial 100, faltas 92 e 46: partes 66,67 e 33,33 (o resto vai para Ana);
  - adiantamento 276, faltas 92 e 46: partes 184 e 92;
  - tudo em dia, total 138, faltas 0 e 0: partes 92 e 46, pelo peso;
  - responsável fora da lista (peso 0), membros Lia 1 e Rui 1, total 100,01, faltas 0 e 0: partes 50,01 e 50,00 (o resto vai para Lia, a primeira);
  - a soma das partes é igual ao total em todos os casos.
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar e ver passar.
- [ ] Passo 5: commitar com a mensagem "Pagamento da família: regra de divisão (testes)".

### Tarefa 7: FamiliaService (a família pronta para a página)

**Arquivos:**
- Modificar: `src/main/java/com/natal/caixinha/lancamento/LancamentoRepository.java`
- Criar: `src/main/java/com/natal/caixinha/caixinha/FamiliaService.java`
- Teste: `src/test/java/com/natal/caixinha/caixinha/FamiliaServiceTest.java` (no padrão `@SpringBootTest` + `BancoDeTeste.URL` + `RelogioDeTeste` + `@Transactional`)

**Usa:** `Carne`, `DivisaoFamilia`, `CaixinhaService.cota()` e `.resumo()`, `CalculadoraCaixinha.situacao`, `PessoaService.dependentesDe`.

**Interfaces que esta tarefa entrega:**
- No repositório: `@Query("select l from Lancamento l join fetch l.pessoa where l.pessoa.id in :ids and l.tipo in (DEPOSITO, CREDITO_ANO_ANTERIOR, DEVOLUCAO) order by l.data asc, l.id asc") List<Lancamento> movimentosDe(Collection<Long> ids)`, escrito com os enums totalmente qualificados no JPQL.
- `enum FamiliaService.EstadoDoMes { SEM_VALOR, PAGO, PENDENTE, ATRASADO }`
- `record FamiliaService.Membro(Pessoa pessoa, Situacao situacao)`
- `record FamiliaService.Familia(Pessoa responsavel, List<Pessoa> dependentes, List<Membro> pagantes, BigDecimal cotaFamilia, BigDecimal valorPix, EstadoDoMes estado, boolean cotaFixada, YearMonth mesAtual, int numeroMes, int totalMeses, List<Carne.Talao> carne, List<Lancamento> extrato)`
- `FamiliaService.Familia daFamilia(Long responsavelId)`
- `List<DivisaoFamilia.Parte> FamiliaService.sugerirDivisao(Long responsavelId, BigDecimal total)`

Regras:
- O estado do mês é SEM_VALOR quando `cotaFamilia` é 0 ou não há pagantes. Nos outros casos, é ATRASADO se algum membro não está em dia, PAGO se todos pagaram a cota do mês, e PENDENTE se nenhum dos dois.
- `valorPix` = soma de `valorParaFicarEmDia` (0 quando o estado é SEM_VALOR ou PAGO).
- `numeroMes` = posição do mês atual entre `mes_inicio` e `mes_fim`, começando em 1.
- O extrato sai em ordem inversa à de `movimentosDe` (mais novo primeiro) e inclui os dependentes de peso 0.

- [ ] Passo 1: escrever os testes. Monte o orçamento do caso da spec (itens somando 10.400, margem de 10%). Com o seed, que tem 11 adultos, o Adolescente (0,5) e o Bebê (0) e soma de pesos 11,5, a cota dá 92. Use a família do seed: "Adulto 1" é o responsável, e "Adolescente" e "Bebê" são os dependentes. **Quem for criar pessoas a mais deve fixar a cota antes** (`CaixinhaService.fixarCota()`), porque pessoas novas mudam a soma de pesos e a cota estimada.
  - `pendenteEmMarco`: com 2 meses pagos por Adulto 1 e Adolescente, o estado é PENDENTE, o `valorPix` é 138, `numeroMes` é 3 e os talões 1 e 2 estão PAGO.
  - `atrasado`: sem depósitos em 15/03, o estado é ATRASADO e o `valorPix` é 414.
  - `pago`: com 3 meses pagos, o estado é PAGO e o `valorPix` é 0.
  - `semOrcamento`: sem itens, o estado é SEM_VALOR, o `valorPix` é 0 e todos os talões estão ABERTO.
  - `responsavelPeso0` (fixa a cota antes de criar as pessoas): avó com peso 0 e dois netos com peso 1. Os pagantes são os 2 netos e a página funciona; `sugerirDivisao(avó, 184)` dá 92 e 92.
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar e ver passar.
- [ ] Passo 5: commitar com a mensagem "Família: situação, carnê e extrato para a página pessoal".

### Tarefa 8: Pagamento da família no painel

**Arquivos:**
- Modificar: `src/main/java/com/natal/caixinha/lancamento/LancamentoService.java`
- Modificar: `src/main/java/com/natal/caixinha/web/LancamentosController.java`
- Modificar: `src/main/java/com/natal/caixinha/web/PainelController.java`
- Modificar: `src/main/resources/templates/painel/inicio.html`
- Modificar: `src/main/resources/templates/painel/lancamentos.html`, com um link "Pagamento da família"
- Criar: `src/main/resources/templates/painel/pagamento-familia.html`
- Teste: `src/test/java/com/natal/caixinha/web/PagamentoFamiliaWebTest.java`

**Interfaces:**
- `LancamentoService.registrarPagamentoDaFamilia(Long responsavelId, BigDecimal total, LocalDate data, List<DivisaoFamilia.Parte> partes): List<Lancamento>`. Roda em `@Transactional`. As regras:
  - total positivo, validado por `Dinheiro.positivo`;
  - a data segue as mesmas regras de `registrar`;
  - cada parte é ≥ 0, e toda pessoa da lista é o responsável ou um dependente ativo dele;
  - a soma das partes é igual ao total. Se não for, lança `RegraException("As partes somam R$ X, mas o PIX foi de R$ Y.")`;
  - cria um `DEPOSITO` por parte maior que 0, com a descrição `"Pagamento da família: PIX de R$ " + fmt`.
- `GET /painel/lancamentos/familia?responsavel={id}&total={texto opcional}` mostra a família (só responsáveis com 2 ou mais pagantes aparecem no select). Sem `total`, preenche com o `valorPix` de `FamiliaService`. Os campos `parte_{pessoaId}` vêm com `sugerirDivisao`, e o botão "Sugerir divisão" refaz o GET com o total digitado.
- `POST /painel/lancamentos/familia` recebe os parâmetros `responsavelId`, `total`, `data` e `parte_{id}`. Se der certo, redireciona para `/painel/lancamentos` com a mensagem "Pagamento da família lançado: N depósitos.". Se der erro, mostra a mesma tela com a mensagem e os valores digitados.
- No Início, o link "Lançar" de uma pessoa cuja família tem 2 ou mais pagantes aponta para `/painel/lancamentos/familia?responsavel={idDoResponsavel}`. Os outros continuam como estão hoje.

- [ ] Passo 1: escrever os testes web, com `@WithMockUser(roles="ORGANIZADOR")`:
  - GET sem login redireciona para `/entrar`;
  - GET com responsável sugere 92 e 46;
  - POST com 92 e 46 para total 138 cria 2 depósitos com a mesma data;
  - POST com 92 e 40 para total 138 não cria nada e mostra "As partes somam R$ 132,00, mas o PIX foi de R$ 138,00.";
  - POST com uma pessoa de outra família é recusado;
  - o Início mostra o link `/painel/lancamentos/familia?responsavel=` para o Adolescente do seed.
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar a suíte inteira e ver passar.
- [ ] Passo 5: commitar com a mensagem "Painel: pagamento da família vira um depósito por pessoa".

### Tarefa 9: Configurações do PIX

**Arquivos:**
- Modificar: `src/main/java/com/natal/caixinha/caixinha/Caixinha.java`, com o método package-private `configurarPix(String chave, String nome, String cidade)`
- Modificar: `src/main/java/com/natal/caixinha/caixinha/CaixinhaService.java`
- Criar: `src/main/java/com/natal/caixinha/web/ConfiguracoesController.java`
- Criar: `src/main/resources/templates/painel/configuracoes.html`
- Modificar: `src/main/resources/templates/fragmentos.html`, com o item de menu "Configurações"
- Teste: `src/test/java/com/natal/caixinha/web/ConfiguracoesWebTest.java`

**Interfaces:**
- `CaixinhaService.salvarPix(ChavePix.Tipo tipo, String chave, String nome, String cidade)`:
  - normaliza a chave com `ChavePix.normalizar`;
  - exige nome e cidade;
  - grava os textos como o organizador digitou, cortados em 25 e 15. A limpeza para o PIX acontece só em `BrCode`.
- `CaixinhaService.recebedor(): Optional<BrCode.Recebedor>`, vazio enquanto a chave estiver em branco.
- `GET /painel/configuracoes` mostra o formulário, com um select de tipo, a chave, o nome e a cidade. Embaixo, uma prévia "Vai aparecer no PIX como: NOME / CIDADE". Quando já existe recebedor, aparece o bloco "PIX de teste de R$ 1,00", com o copia e cola (`BrCode.gerar(r, 1.00, "TESTE")`), o botão `data-copiar` e o `QrCodeSvg`.
- `POST /painel/configuracoes` salva ou mostra o erro.

- [ ] Passo 1: escrever os testes web:
  - a tela exige login;
  - salvar com CPF válido mostra "Configurações salvas" e um PIX de teste que contém `0111` + o CPF e `54041.00`;
  - um CPF inválido mostra "CPF inválido";
  - o menu do painel tem `/painel/configuracoes`.
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar.
- [ ] Passo 4: rodar e ver passar.
- [ ] Passo 5: commitar com a mensagem "Painel: configurações do PIX com PIX de teste de R$ 1,00".

### Tarefa 10: identidade visual (fonte, cores e tema que segue o celular)

**Arquivos:**
- Criar: `src/main/resources/static/fontes/archivo-latin-wdth-normal.woff2` e `src/main/resources/static/fontes/OFL.txt`
- Modificar: `src/main/resources/static/css/estilo.css`
- Modificar: `src/main/java/com/natal/caixinha/config/Seguranca.java`, liberando `/fontes/**` no `permitAll`
- Modificar: os templates que têm `<meta name="theme-color">`, trocando para `#0F2A23` e adicionando `<meta name="color-scheme" content="light dark">`
- Teste: `src/test/java/com/natal/caixinha/web/SegurancaTest.java`, onde `/fontes/archivo-latin-wdth-normal.woff2` responde 200 sem login

- [ ] **Passo 1: pedir autorização ao usuário para baixar a fonte.** Os arquivos são:
  - `archivo-latin-wdth-normal.woff2`, de `https://cdn.jsdelivr.net/npm/@fontsource-variable/archivo@{versão}/files/archivo-latin-wdth-normal.woff2`. Antes, descubra a versão atual com `curl -s https://data.jsdelivr.com/v1/package/npm/@fontsource-variable/archivo` e fixe-a na URL. Informe o tamanho obtido com `curl -sI`.
  - A licença OFL, do `LICENSE` do mesmo pacote.

  Só baixe depois do "sim".
- [ ] **Passo 2: escrever o teste** em `SegurancaTest`: a fonte abre sem login, com status 200. Rodar e ver falhar.
- [ ] **Passo 3: trocar os tokens em `estilo.css`.**
  - `@font-face` da `Archivo` com `font-weight: 400 800`, `font-stretch: 62% 125%` e `font-display: swap`.
  - `html { font-size: 106.25% }`.
  - Tokens escuros no `:root` com os valores da seção "Identidade visual" da spec.
  - Bloco `@media (prefers-color-scheme: light)` com os tokens claros da spec.
  - Selos de atraso do painel (amarelo e vermelho) e mensagens de erro com contraste AA nos dois temas. Confira com o script de contraste usado na revisão de UX.
  - Nenhuma cor solta fora dos tokens, exceto o papel amarelo e a tinta do carimbo, que são iguais nos dois temas.
- [ ] **Passo 4: rodar a suíte** e ver passar.
- [ ] **Passo 5: verificar no navegador.** Use `preview_start` com `caixinha-demo` (pare o preview antes do `./mvnw package`). Abra o login, o Início, Lançamentos, Orçamento e Pessoas em 375px, nos temas escuro e claro (`resize_window` com `colorScheme`). Confira que nada fica ilegível e que não há rolagem horizontal.
- [ ] **Passo 6: commitar** com a mensagem "Identidade visual: Archivo, verde-mata e tema que segue o celular".

### Tarefa 11: página pessoal completa e rota do `.ics`

**Arquivos:**
- Modificar: `src/main/java/com/natal/caixinha/web/PaginaPessoalController.java`
- Reescrever: `src/main/resources/templates/pessoal/inicio.html`, seguindo o esboço aprovado e a seção "A página" da spec
- Criar: `src/main/resources/static/css/pessoal.css`, com talão, papel, serrilha, carnê, folhinhas e carimbo, copiados do esboço e ajustados aos tokens
- Modificar: `src/main/resources/static/js/painel.js` (o texto do botão depois de copiar vira "Copiado!") ou criar `static/js/pessoal.js`, carregado só na página pessoal, com o mesmo comportamento de `data-copiar`
- Modificar: `src/test/java/com/natal/caixinha/web/PaginaPessoalTest.java`, porque os textos "Olá, ..." e "Você + 1 dependente" mudam

**Usa:** `FamiliaService.daFamilia`, `CaixinhaService.recebedor()` e `.resumo()`, `BrCode`, `QrCodeSvg`, `LembreteIcs`, `Formato`.

**Interface:** `GET /c/{token}/lembrete.ics` responde com:
- `Content-Type: text/calendar; charset=utf-8`;
- `Content-Disposition: attachment; filename="caixinha-da-festa.ics"`;
- `Cache-Control: no-store`;
- 404 com a página `pessoal/link-invalido` quando o token não vale.

O `resumo` é `"Caixinha da festa — depositar " + fmt.reais(cotaFamilia)` quando a cota está fixada e é maior que 0; nos outros casos, `"Caixinha da festa — depositar a cota do mês"`. O início é o maior entre `mes_inicio` e o mês de `dataEntrada` do responsável, e o número de meses vai desse início até `mes_fim`. O link vem de `ServletUriComponentsBuilder.fromCurrentContextPath()` + `/c/` + token.

Os textos exatos estão na seção "A página `/c/{token}`" da spec: saudação, estados do talão, passos do PIX, aviso do recebedor, dica do carnê, lembrete e rodapé. O QR fica num `<details>` aberto pelo botão "Pagar pelo QR Code". Sem chave PIX configurada, aparece o texto da spec no lugar do botão.

- [ ] Passo 1: escrever os testes web. Use a família do seed ("Adulto 1", com "Adolescente" e "Bebê") e o orçamento do caso da spec (cota 92). Nos testes que criam outra família, fixe a cota antes. Os depósitos são lançados por `LancamentoService.registrar`, nas datas 08/01 e 11/02/2027.
  - `pendente`: a página contém "Oi, Adulto 1!", "Esta página é sua, de Adolescente e de Bebê." (o Bebê aparece com "(não paga)", conforme a spec), "Mês 3 de 11", "Cota de março", "R$ 138,00", "Ainda não recebemos.", "Copiar PIX de R$ 138,00", o copia e cola com `5406138.00` e "O PIX vai para";
  - `atrasado`: contém "Para ficar em dia" e não contém "atras", "devendo" nem "cobran" (comparação sem diferenciar maiúsculas);
  - `pago`: contém "Cota de março paga" e não contém "Copiar PIX";
  - `semChavePix`: contém "O PIX aparece aqui assim que o organizador cadastrar a chave." e não contém `br.gov.bcb.pix`;
  - `semOrcamento`: contém "Valor ainda não definido" e não contém "Copiar PIX";
  - `cotaEstimada`: contém "(valor estimado)";
  - `carneComCarimbo`: contém `aria-label="janeiro: pago em 08/01"`;
  - `naoMostraOutraFamilia` (fixa a cota antes): a página de "Adulto 1" não contém o nome nem o valor depositado por "Bruno", que é de outra família e depositou R$ 77,00;
  - `semRecursoExterno`: o HTML não contém `http://` nem `https://` fora do link do `.ics` e do texto do PIX (em particular, não contém `fonts.googleapis`);
  - `icsDaFamilia`: o content-type é `text/calendar`, o conteúdo tem `COUNT=11` e `no-store`;
  - `icsLinkInvalido`: responde 404;
  - `icsComCotaEstimada`: o SUMMARY é "depositar a cota do mês".
- [ ] Passo 2: rodar e ver falhar.
- [ ] Passo 3: implementar o controller, o template, o `pessoal.css` e o JS.
- [ ] Passo 4: rodar a suíte inteira e ver passar.
- [ ] Passo 5: verificar no navegador, em 375px, nos temas escuro e claro. No `caixinha-demo`:
  - cadastre o PIX em Configurações com uma chave de teste (por exemplo, a chave aleatória `123e4567-e12b-12d1-a456-426655440000`);
  - crie o orçamento e os depósitos;
  - abra o link de "Adulto 1" e confira os estados pago, pendente e atrasado (mudando os depósitos);
  - confira que o QR abre, que o botão de copiar funciona e que não há rolagem horizontal;
  - tire um screenshot de cada tema.
- [ ] Passo 6: commitar com a mensagem "Página pessoal: carnê, PIX da família, QR Code e lembrete .ics".

### Tarefa 12: fechamento

**Arquivos:** `README.md` (tabela Estado, Fase 4 "pronta, falta teste no banco") e a memória `caixinha-da-festa.md`.

- [ ] Passo 1: rodar a suíte completa com `./mvnw -q test`. O esperado é 0 falhas; anote o total de testes.
- [ ] Passo 2: atualizar o README e commitar com a mensagem "README: fase 4 pronta para testes".
- [ ] Passo 3: ⚠️ **pedir confirmação ao usuário para o push** (vai junto o commit antigo do README da Fase 3). O deploy no Render é automático depois do push.
- [ ] Passo 4: depois do deploy, confirmar que `/health` responde ok e que `/fontes/archivo-latin-wdth-normal.woff2` responde 200.
- [ ] Passo 5: ⚠️ **pedir ao usuário os testes manuais:**
  - cadastrar a chave PIX real em Configurações;
  - pagar o PIX de teste de R$ 1,00 num banco real;
  - abrir o link pessoal e importar o lembrete no Android e no iPhone.

  A Fase 4 só fecha depois disso.

## Nota sobre textos com nomes

Nunca use artigo com gênero antes de nome de pessoa ("do Theo", "da Ana"), porque o app não sabe o gênero de ninguém. Use "de": "Esta página é sua e de Theo.", "É R$ 92 de Ana e R$ 46 de Theo."
