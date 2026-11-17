# Fase 4 — Página pessoal completa, PIX, .ics e identidade visual

Data: 2026-11-17. Base: a spec "Caixinha da Festa" (seções 3.2, 4.1, 4.2 e 7) e as decisões das fases 1 a 3.
Esboço visual aprovado: `2026-11-17-fase4-mockup.html` (abrir no navegador, em largura de celular).

## Por quê

Em janeiro de 2027 começam os depósitos. Cada responsável recebe o link `/c/{token}` pelo WhatsApp e
precisa conseguir, sem ajuda e sem login, entender se o mês está pago e pagar com um toque. Hoje a
página só diz "os depósitos começam em janeiro". A família tem gente de todas as idades: a página
tem que ser entendida num relance, no celular, e sem tom de cobrança.

## Objetivos

- Página pessoal completa (seção 7 da spec), com o PIX copia e cola já no valor certo.
- QR Code do mesmo PIX, para quem abrir no computador.
- Arquivo `.ics` com o lembrete mensal.
- Tela **Configurações** no painel, para cadastrar a chave PIX, o nome e a cidade do recebedor (sem
  isso não existe PIX).
- Identidade visual nova, aplicada à página pessoal e ao painel.
- "Pagamento da família" em Lançamentos: um PIX da família vira um depósito por pessoa, com a divisão sugerida.

## Fora do escopo (fases seguintes)

- Lembretes por WhatsApp e `AvisoMensal` (fase 5).
- Cronograma e "próxima etapa" (fase 6). Até lá, a página mostra só a data da festa, ou "data em votação".
- Desconto de novembro, desistência, entrada tardia, prestação de contas e escolha da sobra (fase 7).

## O que não pode acontecer (anti-objetivos)

- Um PIX com valor errado, ou que o banco recuse. Isso é pior do que não ter PIX.
- Nome de outra família aparecer na página de alguém. A página só mostra números agregados do grupo.
- Tom de bronca: atraso nunca aparece em vermelho na página pessoal.
- O link pessoal vazar para terceiros: nada de fonte, script ou imagem de outro domínio, `no-store` em
  todas as respostas de `/c/**`.

## Abordagens consideradas

1. **Carnê com carimbo (escolhida).** A página é um carnê de 11 talões, de janeiro a novembro. Cada mês
   pago ganha um carimbo "PAGO" com a data. O talão do mês fica grande no topo, com o PIX. Usa uma
   referência que a família inteira conhece (carnê de loja) e mostra o ano inteiro num relance.
2. **Cartões de situação (o visual atual, polido).** Mais simples de fazer, mas genérico e sem a visão
   do ano.
3. **Linha do tempo vertical, mês a mês.** Mostra o ano, mas ocupa muita rolagem no celular e esconde o
   PIX.

O usuário aprovou a 1 e pediu mais cor, **modo escuro**, textura de papel ou de notinha no talão do PIX e folhinhas nos meses pagos. Depois da revisão de UX, escolheu seguir o tema do celular.

## Identidade visual (decidida com o usuário)

- **Escuro como identidade, mas seguindo o celular** (decisão do usuário depois da revisão de UX:
  pessoas com astigmatismo leem melhor texto escuro em fundo claro). `color-scheme: light dark`; o
  escuro é o padrão e `@media (prefers-color-scheme: light)` troca os tokens. Vale para a página
  pessoal e o painel. Versão clara: fundo `#E4EFE9`, superfície `#FFFFFF`, linhas `#C3D0C9`, texto
  `#17201C`, secundário `#4F5E58`, verde `#1F5E4E` no topo, no botão e no cartão da família, lilás
  `#E3E1FA` com texto `#2E2A8A`. O papel amarelo e o carimbo são iguais nos dois modos.
- **Cores:**
  - fundo verde-mata noturno `#0F2A23`; superfície `#173A31`; linhas `#2C5A4D`;
  - texto `#EEF3EF`; texto secundário `#A9BDB4`;
  - papel de carnê `#FFE08A` (borda `#E9C24E`, texto sobre o papel `#2A2410`, texto secundário `#6B5414`);
  - tinta de carimbo `#3730A3`;
  - lilás do lembrete: fundo `#2E2A6E`, texto `#D6D2FF`.
- **Fonte:** Archivo (variável, eixo de largura). Estreita (`wdth` 62–80) nos números grandes, títulos
  e carimbo; normal no texto. Números com `tabular-nums`. **A fonte é servida pelo próprio app**
  (arquivo `.woff2` em `static/fontes/`, licença OFL junto), nunca pelo Google, para o link pessoal não
  fazer requisição a terceiros. Antes de baixar o arquivo, o builder pede autorização ao usuário (nome, origem e
  tamanho do arquivo).
- **Papel:** o talão do PIX e os talões pagos têm grão (SVG `feTurbulence` embutido no CSS), pautas
  horizontais a cada 26px e, no talão do PIX, a borda de baixo serrilhada como notinha.
  Cada mês pago do carnê é uma **folhinha** destacada do bloco: o mesmo papel com grão, pautas a cada
  16px, picote pontilhado em cima, serrilha embaixo, uma sombra curta e uma leve inclinação alternada
  (−0,8° / +0,6°). Meses não pagos continuam lisos, no verde da superfície.
- **Ousadia num lugar só:** o carnê. O resto (família, lembrete, extrato, painel) fica quieto.
- **Painel do organizador:** recebe as mesmas cores e fonte (troca dos tokens em `estilo.css`), sem
  papel nem carimbo. As telas do painel mantêm a estrutura atual. Os destaques de atraso do painel
  (amarelo e vermelho) continuam existindo, ajustados para o fundo escuro com contraste AA.
- Contraste mínimo AA para texto; foco visível em todos os controles; animação só no "Copiado!"
  e respeitando `prefers-reduced-motion`.

## A página `/c/{token}`, de cima para baixo

Tudo é calculado por **família**: o responsável do link e os dependentes ativos dele, contando só
quem é pagante (peso > 0). Para cada pessoa da família vale a regra 3.2 da spec já implementada em
`CalculadoraCaixinha.situacao` (cota da pessoa, guardado da pessoa).

1. **Saudação:** "Oi, {nome}!" e quem está na família: "Esta página é sua e do Theo." Dependente de
   peso 0 aparece como "(não paga)".
2. **Talão do mês (papel, serrilhado):**
   - canhoto: "Mês {n} de {total}" e "{mês} de {ano}" (n = posição do mês atual na caixinha; uma palavra só para tudo: "mês" e "cota", nunca "parcela");
   - "Cota de {mês}" e o valor da família (soma das cotas dos pagantes);
   - linha de estado, um destes:
     - **pendente, em dia:** "Ainda não recebemos. É R$ 92 da Ana e R$ 46 do Theo (meia cota)." (lista só quem falta pagar o mês);
     - **atrasado:** "Para ficar em dia: R$ X", sem palavra de cobrança, e a mesma divisão por pessoa;
     - **paga:** "Cota de março paga" com o carimbo no talão; o botão de PIX some.
   - valor do PIX = soma de `valorParaFicarEmDia` de cada pagante da família (que já inclui o mês atual);
   - linha "O PIX vai para {NOME DO RECEBEDOR}. Confira esse nome no seu banco antes de pagar." (protege contra o medo de golpe);
   - botão "Copiar PIX de R$ X"; logo abaixo, 3 passos numerados: tocar em Copiar PIX; abrir o app do banco em PIX › Pix Copia e Cola; colar e confirmar;
   - botão de largura total "Pagar pelo QR Code", que abre o QR logo abaixo;
   - o texto copia e cola também aparece num campo só leitura, para quem não conseguir copiar.
   - Antes de janeiro de 2027, o mês atual é janeiro (decisão da fase 3), então já dá para adiantar.
   - Enquanto a cota não for fixada, aparece "(valor estimado)" ao lado do valor.
   - Sem chave PIX configurada: no lugar do botão, "O PIX aparece aqui assim que o organizador cadastrar a chave."
3. **Seu carnê:** 11 talões (do `mes_inicio` ao `mes_fim`), **3 por linha**, com o nome do mês por extenso e "R$ 138" no talão. Dica acima: "Cada mês pago ganha um carimbo de PAGO. O mês com a borda em destaque é o de agora." Cada talão pago tem `aria-label` "janeiro: pago em 08/01".
   - Talão do mês *k* (k = 1..total) está **pago** quando **cada** pagante da família tem
     `guardado ≥ k × cotaDaPessoa`. Assim o carnê nunca diz "pago" para alguém que deve.
   - Data do carimbo: a data do lançamento que fez o último pagante da família alcançar `k × cota`,
     percorrendo os lançamentos de cada pessoa em ordem (data, id) com soma corrente de DEPOSITO +
     CREDITO_ANO_ANTERIOR − DEVOLUCAO. Se uma devolução posterior derrubar a soma, vale a última vez
     em que ela voltou a alcançar o valor.
   - Talão **parcial** (alguém da família pagou parte): sem carimbo, com "faltam R$ X".
   - Mês atual: borda em destaque (amarela no escuro, verde no claro). Meses passados sem pagamento: sem carimbo, sem vermelho. Futuros: apagados.
   - O valor em cada talão é o da família, em reais inteiros quando não há centavos.
4. **A família toda:** "R$ X guardados", barra com o percentual da meta, "X% da meta de R$ Y. N de M
   pagantes estão em dia." (vem de `CaixinhaService.resumo()`; só números do grupo).
5. **Festa:** data da festa ou "data em votação" (o cronograma completo é da fase 6).
6. **Pôr lembrete na agenda do celular**, com a explicação "Todo dia 1º, às 9h, o celular avisa a hora de depositar.": link para `/c/{token}/lembrete.ics`.
7. **Seus depósitos:** lançamentos de DEPOSITO, CREDITO_ANO_ANTERIOR e DEVOLUCAO da família, do mais
   novo para o mais antigo: nome, data e valor (devolução com sinal de menos).
8. Rodapé "Guarde este link" (o texto que já existe) e "Alguma dúvida? Fale com quem organiza a caixinha no WhatsApp."

Família sem nenhum pagante: mostra só saudação, família toda e extrato.

**Cota zero** (ainda não há orçamento, ou a estimativa dá zero) *(decisão do builder)*: o talão mostra
"Valor ainda não definido", sem PIX e sem carimbos; o carnê aparece todo liso. O `.ics` usa o texto
"depositar a cota do mês". Assim nunca sai um PIX de valor aberto nem um carnê todo "PAGO" por engano.

**Família com situações misturadas** *(decisão do builder)*: se qualquer pagante estiver atrasado, o
talão usa o estado "atrasado" e lista o valor de cada pessoa.

### Acessibilidade para pessoas mais velhas (revisão de UX de 2026-11-17)

- Texto nunca menor que 16px; tamanhos em `rem` sobre `html { font-size: 106.25% }`, para respeitar o
  tamanho de fonte escolhido no celular. Nada de `maximum-scale` no viewport (o zoom com dois dedos funciona).
- Contraste AA (4,5:1) em todo texto, nos dois modos, inclusive nos meses futuros do carnê.
- Alvos de toque com pelo menos 48px de altura; ações principais com largura total.
- Linguagem simples e uma palavra por conceito; nenhuma sigla técnica na tela (".ics" não aparece).
- Depois de copiar o PIX, o botão mostra "Copiado!" e os passos continuam visíveis.

## PIX copia e cola (pacote `pix`)

- Classe pura `BrCode` (ou nome parecido) que monta o texto exatamente como a seção 4.1 da spec:
  `00`=`01`; `26` com `00`=`br.gov.bcb.pix` e `01`=chave; `52`=`0000`; `53`=`986`; `54`=valor com ponto
  e 2 casas, omitido se zero; `58`=`BR`; `59`=nome; `60`=cidade; `62` com `05`=txid; `63`=CRC16-CCITT-FALSE
  (poly `0x1021`, init `0xFFFF`) sobre todo o texto incluindo `6304`, 4 hex maiúsculos.
- Nome e cidade: sem acentos (Normalizer NFD), maiúsculas, só `A-Z 0-9` e espaço, cortados em 25 e 15.
- txid: `CX2027` + id do responsável (só alfanumérico, ≤ 25).
- Testes: o CRC com um payload de exemplo conhecido (o exemplo do manual do BR Code do Banco Central;
  o builder confere o valor no manual antes de usar), campo 54 omitido com zero, tamanhos com 2 dígitos,
  acentos removidos, cortes de tamanho.
- **QR Code:** dependência `com.google.zxing:core` (só o core). O `BitMatrix` vira um `<svg>` com
  `<path>` gerado no servidor e embutido na página, dentro de um `<details>`, com fundo branco e borda
  de 4 módulos (QR precisa de contraste claro mesmo no modo escuro). Nenhuma rota nova.
- **Copiar:** `navigator.clipboard.writeText`; se falhar, seleciona o texto do campo. Mostra "Copiado!"
  no próprio botão por 2 segundos. Sem JS, o campo só leitura continua lá.

## Lembrete `.ics` (pacote `agenda`)

- Rota `GET /c/{token}/lembrete.ics`, `Content-Type: text/calendar; charset=utf-8`,
  `Content-Disposition: attachment; filename="caixinha-da-festa.ics"`, `Cache-Control: no-store`.
  Token inválido: 404 igual ao da página.
- Um `VEVENT` de dia inteiro no dia 1, `RRULE:FREQ=MONTHLY;COUNT={meses}` começando em
  `max(mes_inicio, mês de entrada do responsável)`, terminando em `mes_fim`.
- `SUMMARY`: "Caixinha da festa — depositar R$ {cota da família}" quando a cota está fixada. Enquanto
  é estimativa: "Caixinha da festa — depositar a cota do mês" (para não gravar no celular de todo mundo
  um valor que ainda vai mudar).
- `DESCRIPTION`: o link pessoal completo (montado com a URL pública do app).
- `VALARM` às 9h do dia (`TRIGGER:PT9H` a partir do início do dia), `ACTION:DISPLAY`.
- `UID` estável: `caixinha-{ano}-pessoa-{id}@caixinha-da-festa`. `DTSTAMP` do relógio do app.
- CRLF, escape de `,` `;` `\` e quebra de linha, e linhas dobradas em 75 octetos.
- Gerador puro com testes unitários (o texto inteiro esperado para um caso, a dobra de linha, o escape,
  o início no mês de entrada).

## Pagamento da família (painel, Lançamentos)

Decisão do usuário: entra nesta fase. A página pede **um PIX por família**, mas os depósitos são
lançados **por pessoa** (decisão da fase 3) e o carnê depende disso. Este atalho evita fazer as
contas e os N lançamentos à mão.

- Em `/painel/lancamentos`, uma opção "Pagamento da família": escolhe o responsável, digita o total
  que chegou e a data. Só aparece para famílias com 2 ou mais pagantes (as outras usam o depósito normal).
- O app sugere a divisão, um campo por pagante, que o organizador pode ajustar antes de salvar:
  1. cada pessoa recebe primeiro o que precisa para ficar em dia (`valorParaFicarEmDia`, que inclui o mês atual);
  2. se o total for **menor** que a soma disso, divide proporcionalmente ao que cada um deve;
  3. se for **maior** (adiantamento), a sobra é dividida proporcionalmente ao peso de cada um;
  4. centavos: arredonda cada parte para baixo e o resto vai para o responsável.
- Ao salvar: a soma das partes tem que ser igual ao total (senão, erro claro dizendo a diferença). Cria
  um `DEPOSITO` por pessoa com valor maior que zero, todos com a mesma data e a descrição
  "Pagamento da família: PIX de R$ X". Tudo numa transação só.
- O link "Lançar" do Início, quando a pessoa é de uma família com 2 ou mais pagantes, abre essa opção já com o
  responsável e o valor da família.
- A divisão é uma regra pura com testes unitários (exato, parcial, adiantamento, centavos, dependente de peso 0).

## Configurações (painel, `/painel/configuracoes`)

- Campos: chave PIX (≤ 77), nome do recebedor (≤ 25), cidade (≤ 15). As colunas já existem na tabela
  `caixinha` desde a V1: **não há migração nesta fase**.
- **Tipo da chave escolhido pelo organizador** *(decisão do builder)*, num seletor: CPF, CNPJ, celular,
  e-mail ou chave aleatória. CPF e celular têm 11 dígitos, então adivinhar o tipo poderia gerar um PIX
  recusado. Validação por tipo: CPF e CNPJ com dígitos verificadores; celular vira `+55` + DDD + número;
  e-mail em minúsculas; aleatória como UUID. O tipo não é gravado (a coluna não existe e não precisa:
  ele só serve para validar e normalizar na hora de salvar).
- Nome e cidade: mostra como vão sair no PIX (sem acento, maiúsculas, cortado).
- **PIX de teste:** depois de salvar, a tela mostra um PIX de R$ 1,00 (copia e cola e QR) para o
  organizador testar no banco. É o teste manual da spec.
- Novo item "Configurações" no menu do painel.
- As datas (festa, meses, fechamento da lista) ficam para a fase 6, junto com o cronograma.

## Testes e verificação

- Unitários: `BrCode`, gerador `.ics`, regra do carnê (pago, parcial, atrasado, adiantado em dez/2026,
  devolução que derruba um mês, família com dependente de meia cota e de peso 0).
- Web (MockMvc): página com cada estado do talão; 404 e `no-store` em `/c/**` e no `.ics`; `.ics`
  com content-type certo; página não mostra nome de outra família; Configurações salva e recusa chave
  inválida; o menu tem o item novo.
- Verificação no navegador, em 375px, com os dados de demonstração: página pessoal nos estados pago,
  pendente e atrasado; QR legível; copiar funciona; painel legível no escuro.
- ⚠️ **Testes do usuário antes de fechar a fase:** colar o PIX de teste num banco real; importar o
  `.ics` no Android e no iPhone.

## Ordem de entrega (commits pequenos)

1. Gerador de PIX + testes. 2. Gerador de `.ics` + testes. 3. Regra do carnê + testes. 4. Pagamento da família + testes. 5. Tela de
Configurações. 6. Identidade visual (tokens, fonte, painel). 7. Página pessoal completa, QR e `.ics`.
8. README. Push e deploy só com a confirmação do usuário.

## Deixado para o builder

- Nomes exatos de classes e métodos, e onde a regra da família mora (provavelmente um serviço novo no
  pacote `caixinha` que reaproveita `CalculadoraCaixinha`).
- O desenho do QR (tamanho em px) e o texto exato de mensagens curtas, seguindo o tom desta página.
