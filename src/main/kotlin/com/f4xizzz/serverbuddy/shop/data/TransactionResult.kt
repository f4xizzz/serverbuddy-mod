package com.f4xizzz.serverbuddy.shop.data

class TransactionResult(
    val success: Boolean, // Indicates if the transaction was successful
    val response: List<String> = listOf(), // Response message if failed
    val amount: Int // Amount Processed
)