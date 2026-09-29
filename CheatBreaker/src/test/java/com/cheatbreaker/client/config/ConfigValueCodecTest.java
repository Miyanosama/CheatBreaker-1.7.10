package com.cheatbreaker.client.config;

public final class ConfigValueCodecTest {
    private static void expect(Object actual, Object expected) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        expect(ConfigValueCodec.parse(Setting.Type.FLOAT, "1.2", 0.5f, 1.5f, null), 1.2f);
        expect(ConfigValueCodec.parse(Setting.Type.FLOAT, "0.5", 0.5f, 1.5f, null), 0.5f);
        expect(ConfigValueCodec.parse(Setting.Type.FLOAT, "1.5", 0.5f, 1.5f, null), 1.5f);
        expect(ConfigValueCodec.parse(Setting.Type.FLOAT, "0.4", 0.5f, 1.5f, null), null);
        expect(ConfigValueCodec.parse(Setting.Type.FLOAT, "NaN", 0.5f, 1.5f, null), null);
        expect(ConfigValueCodec.parse(Setting.Type.DOUBLE, "Infinity", null, null, null), null);
        expect(ConfigValueCodec.parse(Setting.Type.INTEGER, "12", 0, 20, null), 12);
        expect(ConfigValueCodec.parse(Setting.Type.INTEGER, "21", 0, 20, null), null);
        expect(ConfigValueCodec.parse(Setting.Type.INTEGER, "42", null, null, null), 42);
        expect(ConfigValueCodec.parse(Setting.Type.BOOLEAN, "true", null, null, null), true);
        expect(ConfigValueCodec.parse(Setting.Type.BOOLEAN, "maybe", null, null, null), null);
        expect(ConfigValueCodec.parse(Setting.Type.STRING_ARRAY, "borderless", null, null,
                new String[] {"Borderless", "Exclusive"}), "Borderless");
        expect(ConfigValueCodec.parse(Setting.Type.STRING_ARRAY, "Other", null, null,
                new String[] {"Borderless", "Exclusive"}), null);
        expect(ConfigValueCodec.parse(Setting.Type.STRING, "test", null, null, null), "test");
        System.out.println("Config value checks passed");
    }
}
