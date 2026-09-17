
package com.example

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

fun main() {
    val enc = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDySan+n+AW12BvOaQj7cuGfg8Ed085rYUtqDj8DQY3nIMQdr42ScGdtRw7tS9a8Gtq"
    val key = "38346591".toByteArray(Charsets.UTF_8)
    val keySpec = SecretKeySpec(key, "DES")
    val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    val decrypted = cipher.doFinal(Base64.getDecoder().decode(enc))
    val url = String(decrypted, Charsets.UTF_8)
    println("Decrypted in Kotlin: $url")
}
