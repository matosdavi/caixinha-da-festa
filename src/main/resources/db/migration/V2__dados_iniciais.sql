-- Dados iniciais: a configuração da caixinha 2027 e as 13 pessoas da família
-- com nomes provisórios (o organizador edita no painel).
-- Chave PIX, recebedor e data da festa ficam em branco até serem configurados.

INSERT INTO caixinha (id, nome, mes_inicio, mes_fim, margem_imprevistos, data_fechamento_lista)
VALUES (1, 'Caixinha da Festa 2027', '2027-01', '2027-11', 0.10, DATE '2026-12-31');

-- 11 adultos (cota inteira)
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 1', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 2', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 3', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 4', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 5', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 6', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 7', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 8', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 9', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 10', 1.00, DATE '2026-11-16');
INSERT INTO pessoa (nome, peso, data_entrada) VALUES ('Adulto 11', 1.00, DATE '2026-11-16');

-- Adolescente (meia cota) e bebê (não paga), sob a responsabilidade de um dos pais.
-- Provisório: "Adulto 1" como responsável; o organizador ajusta no painel.
INSERT INTO pessoa (nome, peso, data_entrada, responsavel_id)
SELECT 'Adolescente', 0.50, DATE '2026-11-16', id FROM pessoa WHERE nome = 'Adulto 1';
INSERT INTO pessoa (nome, peso, data_entrada, responsavel_id)
SELECT 'Bebê', 0.00, DATE '2026-11-16', id FROM pessoa WHERE nome = 'Adulto 1';
