package com.dogechat.android

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.libdohj.params.DogecoinMainNetParams

/**
 * Thin helper around address/amount parsing (kept separate to satisfy existing references).
 */
object TransactionHandler {
    private val params: NetworkParameters = DogecoinMainNetParams.get()

    fun parseAddress(addr: String): Address = Address.fromString(params, addr)

    fun parseAmountDogeToCoin(amountDoge: Long): Coin =
        Coin.valueOf(amountDoge * 100_000_000L)

    fun isValidAddress(addr: String): Boolean =
        runCatching { Address.fromString(params, addr); true }.getOrElse { false }
}
