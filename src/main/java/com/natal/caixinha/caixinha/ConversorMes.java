package com.natal.caixinha.caixinha;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/** Guarda um YearMonth como texto 'AAAA-MM' (legível no banco e ordenável). */
@Converter(autoApply = true)
public class ConversorMes implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth mes) {
        return mes == null ? null : mes.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String texto) {
        return texto == null ? null : YearMonth.parse(texto.trim());
    }
}
