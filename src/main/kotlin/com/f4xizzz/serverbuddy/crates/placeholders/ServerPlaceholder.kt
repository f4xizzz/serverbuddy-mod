package com.f4xizzz.serverbuddy.crates.placeholders

interface ServerPlaceholder {
    fun handle(args: List<String>): GenericResult
    fun id(): String
}
