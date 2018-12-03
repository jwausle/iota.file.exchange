package iota.file.exchange

import java.io.ByteArrayInputStream
import java.net.URL
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.spec.SecretKeySpec


/** Smart Contract to up- and download authorized documents */
object Contract {
    data class FileExchange(val token: TangleMock.Token, val link: URL, val permissions: MutableList<Permission>) {
        fun addPermission(permission: Permission) {
            if (!permissions.contains(permission)) {
                permissions.add(permission)
            }
        }

        fun getOwnerPermission(): Permission.Owns {
            val onlyOwner = this.permissions.filter { it is Permission.Owns }
            if (onlyOwner.size == 1) {
                return onlyOwner[0] as Permission.Owns
            }
            throw IllegalStateException("Exact one Owns permission must exist")
        }

        fun hasReadPermission(publicKey: PublicKey): Boolean {
            return permissions.any { it.getPublicKey() == publicKey }
        }

        fun toJson(): String {
            return "FileExchange.toJson() - must implemented"
        }
    }

    /**
     * Permission to encrypt uploaded and decrypt DOWNLOADED content. Can be shared over the tangle.
     */
    sealed class Permission {
        data class Read(val token: TangleMock.Token, internal val rsa: Rsa, val reader: User.Customer) : Permission()
        data class Owns(val token: TangleMock.Token, internal val rsa: Rsa, val encryptedKey: ByteArray, val owner: User.Owner) : Permission() {
            fun decryptKey(): Key {
                val decrypted = decrypt(ByteArrayInputStream(encryptedKey), rsa.privateKey)
                return SecretKeySpec(decrypted, "AES")
            }
        }

        companion object {
            fun createOwnsPermission(email: String): Owns {
                val rsa = createRsa()
                val aes = createAes()
                val encryptedAes = encrypt(aes.encoded, rsa.publicKey)
                return Owns(TangleMock.getToken(), rsa, encryptedAes, User.Owner(email))
            }

            fun createReadPermission(email: String): Read {
                return Read(TangleMock.getToken(), createRsa(), User.Customer(email))
            }
        }

        fun getPublicKey(): PublicKey = when (this) {
            is Owns -> this.rsa.publicKey
            is Read -> this.rsa.publicKey
        }

        fun getPrivateKey(): PrivateKey = when (this) {
            is Owns -> this.rsa.privateKey
            is Read -> this.rsa.privateKey
        }

        fun toJson(): String {
            return "Permission.toJson() - must implemented"
        }
    }

    sealed class User {
        data class Owner(val email: String) : User()
        data class Customer(val email: String) : User()

        fun getSeed(): TangleMock.Seed {
            return TangleMock.Seed("User.getSeed() - must implemented")
        }
    }

    data class Rsa(internal val publicKey: PublicKey, internal val privateKey: PrivateKey)
}