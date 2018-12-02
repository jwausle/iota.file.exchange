package iota.file.exchange

import java.net.URL

/**
 * iota.file.exchange.TangleMock mock IOTA to focus the iota.file.exchange.main case of the FileExchange process.
 */
object TangleMock {
    data class Token(val token: ByteArray) {
        override fun toString(): String = tokenToString(this)
    }

    data class Seed(val seed: String)
    data class Trytes(val seed: Seed, val bytes: ByteArray)
    data class Bundle(val trytes: Trytes)
    class Transaction(val trytes: Trytes) {

        private val depth = 3
        private val minWeightMagnitude = 14

        fun send(): Unit = try {
            val bundle: Bundle = sendTrytes(this.trytes, this.depth, minWeightMagnitude)
        } catch (e: Exception) {
            out.printException("Couldn't sent trytes to tangle because - ${e.message}", e)
        }
    }

    // Cache to find FileExchange by URL. This represent the Tangle.
    private val fileExchanges: LinkedHashMap<URL, Contract.FileExchange> = LinkedHashMap()

    fun getToken(): Token {
        return Token(randomSha256())
    }

    fun tokenToString(token: Token): String {
        val buffer = StringBuffer()
        token.token.iterator().forEach { buffer.append(String.format("%02x", it)) }
        return buffer.toString()
    }

    fun find(link: URL): Contract.FileExchange? {
        return fileExchanges.get(key = link)
    }

    fun prepareTransfer(seed: Seed, fileExchange: Contract.FileExchange): Transaction {
        fileExchanges[fileExchange.link] = fileExchange
        return prepareTransfer(seed, fileExchange.toJson());
    }

    fun prepareTransfer(seed: Seed, transfer: String): Transaction {
        return Transaction(Trytes(seed, transfer.toByteArray()))
    }

    fun sendTrytes(trytes: Trytes, depth: Int, minWeightMagnitude: Int): Bundle {
        return Bundle(trytes)
    }
}