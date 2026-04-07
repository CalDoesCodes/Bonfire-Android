package com.example.bonfire

enum class ChatType {
    GLOBAL, PRIVATE, GROUP;
    companion object {
        private val VALUES = ChatType.entries.toTypedArray()
        fun getByValue(value: Int) = VALUES.firstOrNull { it.ordinal == value }
        fun getByName(value: String) = VALUES.firstOrNull { it.name == value }
    }
}