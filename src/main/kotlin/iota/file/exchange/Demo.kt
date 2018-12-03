package iota.file.exchange

import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.crypto.spec.SecretKeySpec

private val CDN = ContentDeliveryNetwork()
private val OWNER = Contract.User.Owner("jan.winter@itemis.de")
private val CUSTOMER = Contract.User.Customer("winterjan@hotmail.com")
private val FILE_TO_UPLOAD: Path = Files.createTempFile("upload-", ".source")

private const val DEFAULT_LINE_WIDTH = 120
val out = PrettyPrinter(DEFAULT_LINE_WIDTH)

/**
 * OWNER upload a file to the CDN
 * Grant read permission to CUSTOMER
 * Send email to CUSTOMER
 * CUSTOMER download encrypted file and decrypt key
 * and decrypt the file content
 */
fun main(args: Array<String>) {
    val content = Collections.nCopies(1000, "a").joinToString(separator = "").toByteArray()
    Files.write(FILE_TO_UPLOAD, content)
    out.printHeadline("# Wrote ${content.size} bytes to ${FILE_TO_UPLOAD} as backup")

    out.printHeadline("# Owner uploading file with ${content.size} bytes ... ")
    val link: URL = CDN.upload(FILE_TO_UPLOAD.toFile().inputStream(), OWNER)
    out.printHeadline("# Owner granting read permission to user for ${link} ... ")
    val readPermission = CDN.grantReadPermission(link, OWNER, CUSTOMER)

    //send email with link
    out.printHeadline("# Owner sending mail with link to customer ... ")

    out.printHeadline("# User downloading ${link} ... ")
    val contentEncrypted = CDN.download(link)
    out.printHeadline("# User downloaded ${contentEncrypted.size} encrypted bytes ... ")

    out.printHeadline("# User fetching aes key (encrypted) ... ")
    val aesKeyEncrypted = CDN.fetchKey(link, readPermission.getPublicKey())
    val aesKeyDecrypted = decrypt(ByteArrayInputStream(aesKeyEncrypted), readPermission.getPrivateKey())
    val aesKey = SecretKeySpec(aesKeyDecrypted, "AES")

    out.printHeadline("# User decrypting content bytes ... ")
    val contentDecrypted = decrypt(ByteArrayInputStream(contentEncrypted), aesKey)
    out.printHeadline("# User decrypted ${contentDecrypted.size} bytes:")
            .println("${String(contentDecrypted)}")
}

/**
 * Helper to pretty print stdout.
 */
class PrettyPrinter(private val lineLength: Int, private val indentToken: String = "  ", private var printStacktraces: Boolean = false) {
    private var indent = ""

    fun println(string: String): PrettyPrinter {
        System.out.println(this.indent + string
                .replace("\n", "\n${this.indent}")
                .chunked(this.lineLength)
                .joinToString("\n${this.indent}"))
        return this
    }

    fun printHeadline(headline: String): PrettyPrinter {
        this.unindent()
        this.println(headline)
        this.indent()
        return this
    }

    fun indent(): PrettyPrinter {
        this.indent = indent + indentToken
        return this
    }

    fun unindent(): PrettyPrinter {
        if (this.indent.endsWith(indentToken)) {
            this.indent = this.indent.substring(0, this.indent.length - indentToken.length)
        }
        return this
    }

    fun printException(error: String, e: Exception) {
        printHeadline("${e.javaClass.simpleName} - ${error} because - ${e.message}")
        if (printStacktraces) {
            e.printStackTrace()
        }
    }
}