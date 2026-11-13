package com.f4xizzz.serverbuddy.shop.placeholders

interface ServerPlaceholder {
    fun handle(args: List<String>): GenericResult
    fun id(): String
}
