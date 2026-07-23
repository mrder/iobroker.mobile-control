package com.mobilecontrol.app.domain.model

enum class ThemeMode(val wireName: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromWireName(value: String?): ThemeMode = entries.firstOrNull { it.wireName == value } ?: SYSTEM
    }
}
