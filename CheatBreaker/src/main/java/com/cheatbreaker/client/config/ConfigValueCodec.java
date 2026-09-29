package com.cheatbreaker.client.config;

/** Shared validation for values loaded from global and profile files. */
public final class ConfigValueCodec {
    private ConfigValueCodec() {}

    public static Object parse(Setting.Type type, String text, Number minimum, Number maximum,
                               String[] acceptedValues) {
        if (type == null || text == null) return null;
        try {
            switch (type) {
                case BOOLEAN:
                    if ("true".equalsIgnoreCase(text)) return Boolean.TRUE;
                    if ("false".equalsIgnoreCase(text)) return Boolean.FALSE;
                    return null;
                case INTEGER: {
                    int value = Integer.parseInt(text);
                    return inRange(value, minimum, maximum) ? value : null;
                }
                case FLOAT: {
                    float value = Float.parseFloat(text);
                    return inRange(value, minimum, maximum) ? value : null;
                }
                case DOUBLE: {
                    double value = Double.parseDouble(text);
                    return inRange(value, minimum, maximum) ? value : null;
                }
                case STRING_ARRAY:
                    if (acceptedValues != null) {
                        for (String accepted : acceptedValues) {
                            if (accepted.equalsIgnoreCase(text)) return accepted;
                        }
                    }
                    return null;
                case STRING:
                    return text;
                default:
                    return null;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean inRange(double value, Number minimum, Number maximum) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && (minimum == null || value >= minimum.doubleValue())
                && (maximum == null || value <= maximum.doubleValue());
    }
}
