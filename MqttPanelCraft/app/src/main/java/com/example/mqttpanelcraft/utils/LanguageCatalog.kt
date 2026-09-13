package com.example.mqttpanelcraft.utils

data class LanguageOption(
    val code: String,
    val nativeName: String
)

object LanguageCatalog {
    val options: List<LanguageOption> = listOf(
        LanguageOption(LocaleManager.CODE_EN, "English"),
        LanguageOption(LocaleManager.CODE_ZH, "繁體中文"),
        LanguageOption(LocaleManager.CODE_CN, "简体中文")
    )
}
