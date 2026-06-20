package com.ecommerce.agent.util;

public final class CountryLanguageUtil {

    private CountryLanguageUtil() {
    }

    public static String resolveCountryName(String code) {
        if (code == null) return "美国";
        return switch (code.toUpperCase()) {
            case "US" -> "美国";
            case "UK" -> "英国";
            case "JP" -> "日本";
            case "DE" -> "德国";
            case "FR" -> "法国";
            case "KR" -> "韩国";
            case "AU" -> "澳大利亚";
            case "CA" -> "加拿大";
            default -> code;
        };
    }

    public static String resolveLanguage(String country) {
        if (country == null) return "English";
        return switch (country.toUpperCase()) {
            case "JP" -> "Japanese";
            case "DE" -> "German";
            case "FR" -> "French";
            case "KR" -> "Korean";
            default -> "English";
        };
    }
}
