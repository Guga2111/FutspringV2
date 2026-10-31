package com.futspring.backend.domain.finance.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Do not silently accept JSON numbers (or their binary floating point approximations) as money. */
public class DecimalStringDeserializer extends ValueDeserializer<String> {
    @Override public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(String.class, "Valores financeiros devem ser strings decimais");
        }
        return parser.getString();
    }
}
