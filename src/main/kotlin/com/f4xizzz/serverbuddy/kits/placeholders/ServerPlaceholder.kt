package com.f4xizzz.serverbuddy.kits.placeholders

interface ServerPlaceholder {
    fun handle(args: List<String>): GenericResult
    fun id(): List<String>
}
