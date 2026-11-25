-- Fase 7: acréscimo cobrado pelo anfitrião por quem entrou depois do fechamento da lista.
-- Nulo = ainda não confirmado; 0 = confirmado, sem acréscimo. É dividido nos meses de acrescimo_desde até o fim.
ALTER TABLE pessoa ADD COLUMN acrescimo NUMERIC(12,2) CHECK (acrescimo >= 0);
ALTER TABLE pessoa ADD COLUMN acrescimo_desde VARCHAR(7);
