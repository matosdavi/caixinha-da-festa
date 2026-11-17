# Caixinha da Festa

App da família para juntar o dinheiro da festa de fim de ano (dezembro de 2027).
Cada pessoa deposita uma cota mensal de **janeiro a novembro de 2027**. O organizador lança os
depósitos no painel e cada responsável acompanha a própria situação por um **link pessoal**, sem
cadastro nem senha.

É um app **separado** do [amigo secreto](https://github.com/matosdavi/amigo-secreto), mas usa a mesma base:
Java/Spring Boot, Render e Neon.

## Estado

| Fase | Entrega | Situação |
|---|---|---|
| 1 | Projeto, Flyway, seed, deploy, login do organizador | ✅ no ar em https://caixinha-da-festa.onrender.com |
| 2 | Pessoas, responsáveis, links | ✅ código pronto |
| 3 | Lançamentos e cálculos (cota, situação, atrasos) | — |
| 4 | Página pessoal completa, PIX copia e cola, .ics | — |
| 5 | Lembretes e WhatsApp | — |
| 6 | Orçamento e cronograma | — |
| 7 | Desconto de novembro, desistência, entrada tardia, prestação de contas | — |

## Tecnologias

| Item | Versão |
|---|---|
| Java | 17 ou superior (testado com 21) |
| Spring Boot | 4.0: Web MVC, Thymeleaf, Data JPA, Security, Flyway |
| Banco | PostgreSQL (produção: Neon) · H2 em modo PostgreSQL para desenvolvimento e testes |
| Fuso | `America/Sao_Paulo` em todo cálculo de data |
| Dinheiro | `BigDecimal` no Java, `NUMERIC(12,2)` no banco, nunca ponto flutuante |

O schema é criado **só pelo Flyway** (`src/main/resources/db/migration`). O Hibernate apenas valida
(`ddl-auto=validate`). **Nunca edite uma migração que já rodou em produção**: crie uma nova (`V3__...`).

## Executando localmente

```bash
# Windows (com o JDK 21 no JAVA_HOME)
mvnw.cmd spring-boot:run
# Linux / macOS
./mvnw spring-boot:run
```

Abra **http://localhost:8080/painel**. Sem `ADMIN_SENHA`, o console mostra um login temporário.

Por padrão o banco é um H2 em arquivo (`./data`). Para testar com PostgreSQL de verdade:
`docker compose up -d` e `DATABASE_URL=postgres://postgres:postgres@localhost:5434/caixinha?sslmode=disable` no `.env`.

> ⚠️ Não aponte o `.env` local para o Neon de produção. Testes locais sujariam os dados reais.

Testes: `./mvnw test`.

## Produção: Render + Neon

| Peça | Serviço (plano grátis) | O que configurar |
|---|---|---|
| Banco | [Neon](https://neon.com): um **projeto novo**, separado do amigo secreto, região **AWS US East 2 (Ohio)** | copiar a connection string para `DATABASE_URL` |
| Aplicação | [Render](https://render.com) → **New → Web Service** a partir deste repositório, runtime **Docker**, região **Ohio**, plano **Free** | variáveis abaixo e **Health Check Path** = `/health` |

Variáveis de ambiente no Render (o `Dockerfile` já define `APP_ENV=production` e `PORT`):

```
DATABASE_URL  = postgresql://usuario:senha@ep-xxxx.us-east-2.aws.neon.tech/neondb?sslmode=require
ADMIN_USUARIO = organizador
ADMIN_SENHA   = uma senha forte com 12 ou mais caracteres
```

Em produção, o app **se recusa a subir** se faltar `DATABASE_URL` ou se a `ADMIN_SENHA` for curta.
Na primeira subida, o Flyway cria as tabelas e o seed (a caixinha 2027 e 13 pessoas provisórias).

**Plano grátis:** as 750 horas/mês do Render são divididas entre todos os serviços grátis da conta.
Como os dois apps (amigo secreto e caixinha) dormem depois de 15 minutos sem acesso, isso sobra.
O primeiro acesso depois de dormir leva cerca de 1 minuto. **Não use monitor para manter acordado.**

Trocar a `ADMIN_SENHA` desconecta todos os aparelhos que marcaram "manter conectado".
