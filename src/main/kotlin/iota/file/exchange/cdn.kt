package iota.file.exchange

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.*
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

private val store: Path = Files.createTempDirectory("iota.file.exchange.store")

/**
 * User interface to upload and download authorized files.
 */
class ContentDeliveryNetwork {
    /**
     * Upload+iota.file.exchange.encrypt+create FileExchange instance for owner.
     */
    fun upload(content: InputStream, owner: Contract.User.Owner): URL {
        // create owner permission
        val ownsPermission = Contract.Permission.createOwnsPermission(owner.email)
        TangleMock.prepareTransfer(owner.getSeed(), ownsPermission.toJson()).send()
        out.println("CDN sent owns permission to tangle")

        // store encrypted content
        val contentEncrypted = encrypt(content, ownsPermission.decryptKey())
        val link: URL = store(contentEncrypted)
        out.println("CDN stored encrypted file ${link}")

        // create file exchange link for owner
        val exchangeFile = Contract.FileExchange(TangleMock.getToken(), link, mutableListOf(ownsPermission))
        TangleMock.prepareTransfer(owner.getSeed(), exchangeFile).send()
        out.println("CDN sent exchange file with token '${exchangeFile.token}' to tangle")

        return link
    }

    /**
     * Grant read permission for customer.
     */
    fun grantReadPermission(link: URL, by: Contract.User.Owner, to: Contract.User.Customer): Contract.Permission {
        val readPermission = Contract.Permission.createReadPermission(to.email)
        val exchangeFile: Contract.FileExchange? = TangleMock.find(link)

        if (exchangeFile != null) {
            out.printHeadline("CDN found file exchange for ${link}")
            TangleMock.prepareTransfer(to.getSeed(), readPermission.toJson()).send()
            out.println("CDN read owns permission to tangle")

            exchangeFile.addPermission(readPermission)
            TangleMock.prepareTransfer(by.getSeed(), exchangeFile.toJson()).send()
            out.println("CDN update exchange file with token '${exchangeFile.token}' in tangle")
            return readPermission
        }
        throw IllegalArgumentException("File ${link} not exist")
    }

    /**
     * Download encrypted content.
     */
    fun download(link: URL): ByteArray {
        val exchangeFile: Contract.FileExchange? = TangleMock.find(link)
        if (exchangeFile != null) {
            out.printHeadline("CDN found file exchange for ${link}")
            return link.readBytes()
        }
        throw IllegalArgumentException("File ${link} not exist")
    }

    /**
     * Fetch the encrypted key to iota.file.exchange.decrypt the downloaded content.
     */
    fun fetchKey(link: URL, publicKey: PublicKey): ByteArray {
        val exchangeFile: Contract.FileExchange? = TangleMock.find(link)
        if (exchangeFile != null) {
            if (exchangeFile.hasReadPermission(publicKey)) {
                val aesKey: Key = exchangeFile.getOwnerPermission().decryptKey()
                out.println("CDN decrypt aes key as owner")
                val aesKeyEncrypted = encrypt(ByteArrayInputStream(aesKey.encoded), publicKey)
                out.println("CDN encrypted aes key for reader")
                return aesKeyEncrypted
            }
            throw IllegalArgumentException("PublicKey has no read permission for $link")
        }
        throw IllegalArgumentException("File $link not exist")
    }

    private fun store(bytes: ByteArray): URL {
        val fileName = sha1(bytes).map { String.format("%02x", it) }.joinToString("")
        val localPath = store.resolve(fileName)
        Files.write(localPath, bytes)
        return localPath.toUri().toURL()
    }
}

fun encrypt(input: InputStream, privateKey: PrivateKey): ByteArray {
    //    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    val cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, privateKey)

    return input.use { cipher.doFinal(it.readBytes()) }
}

fun encrypt(input: InputStream, publicKey: PublicKey): ByteArray {
    //    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    val cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)

    return input.use { cipher.doFinal(it.readBytes()) }
}

fun encrypt(bytes: ByteArray, publicKey: PublicKey): ByteArray {
    return encrypt(ByteArrayInputStream(bytes), publicKey)
}

fun decrypt(input: InputStream, publicKey: PublicKey): ByteArray {
    val cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.DECRYPT_MODE, publicKey)

    return input.use { cipher.doFinal(it.readBytes()) }
}

fun decrypt(input: InputStream, privateKey: PrivateKey): ByteArray {
    val cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.DECRYPT_MODE, privateKey)

    return input.use { cipher.doFinal(it.readBytes()) }
}

fun encrypt(input: InputStream, aesKey: Key): ByteArray {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey)

    val encrypted = input.use { cipher.doFinal(it.readBytes()) }
    return encrypted
}

fun decrypt(input: InputStream, aesKey: Key): ByteArray {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, aesKey)

    return input.use { cipher.doFinal(it.readBytes()) }
}

fun randomSha256(): ByteArray {
    val uuid = UUID.randomUUID().toString().toByteArray(Charset.forName("UTF-8"))
    return MessageDigest.getInstance("SHA-256").digest(uuid)
}

fun sha1(bytes: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-1").digest(bytes)
}

val plaintext = ByteArray(128)

fun createRsa(): Contract.Rsa {
    val random = SecureRandom.getInstance("SHA1PRNG")
    /* constant 117 is a public key size - 11 */
    random.nextBytes(plaintext)

    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(1024, random)
    val keyPair = keyGen.genKeyPair()
    return Contract.Rsa(keyPair.public, keyPair.private)
}

fun createAes(): Key {
    val keygen = KeyGenerator.getInstance("AES")
    keygen.init(128)
    return keygen.generateKey()
}
