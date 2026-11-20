package com.tua.baul

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Un archivo guardado en la bóveda. */
data class VItem(
    val file: String,   // nombre del archivo dentro de la bóveda
    val name: String,   // nombre original
    val mime: String,
    val date: Long,     // fecha de la foto (ms)
    val path: String    // carpeta original, para devolverlo a su lugar
) {
    val video get() = esVideoMime(mime)
}

/**
 * La bóveda vive en el espacio privado de la app: ninguna otra app la puede ver,
 * y en la galería no aparece por ningún lado. Se abre escribiendo la clave en el buscador.
 */
object Boveda {

    fun dir(c: Context) = File(c.filesDir, "boveda").apply { mkdirs() }
    private fun indice(c: Context) = File(dir(c), "indice.json")
    private fun prefs(c: Context) = c.getSharedPreferences("privado", Context.MODE_PRIVATE)

    // ---------- Clave ----------

    fun tieneClave(c: Context) = prefs(c).contains("hash")

    private fun derivar(pin: String, salt: ByteArray, bits: Int = 256): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 20000, bits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun ponerClave(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(derivar(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun comprobar(c: Context, pin: String): Boolean {
        if (pin.length < 4) return false
        val p = prefs(c)
        val salt = Base64.decode(p.getString("salt", null) ?: return false, Base64.NO_WRAP)
        val hash = Base64.decode(p.getString("hash", null) ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(derivar(pin, salt), hash)
    }

    // ---------- Índice ----------

    fun items(c: Context): List<VItem> {
        val f = indice(c)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                VItem(o.getString("file"), o.optString("name"), o.optString("mime"), o.optLong("date"), o.optString("path"))
            }.filter { File(dir(c), it.file).exists() }.sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun guardar(c: Context, list: List<VItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("file", it.file).put("name", it.name).put("mime", it.mime)
                    .put("date", it.date).put("path", it.path)
            )
        }
        val tmp = File(dir(c), "indice.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indice(c))
    }

    fun archivo(c: Context, item: VItem) = File(dir(c), item.file)

    // ---------- Meter y sacar ----------

    /** Copia fotos/videos a la bóveda. Devuelve los que se copiaron bien. */
    fun meter(c: Context, medias: List<Media>): List<Pair<Media, VItem>> {
        val hechos = mutableListOf<Pair<Media, VItem>>()
        for (m in medias) {
            val ext = m.name.substringAfterLast('.', if (m.video) "mp4" else "jpg")
            val nombre = UUID.randomUUID().toString() + "." + ext
            val destino = File(dir(c), nombre)
            try {
                c.contentResolver.openInputStream(m.uri)?.use { input ->
                    destino.outputStream().use { input.copyTo(it) }
                } ?: continue
                hechos += m to VItem(nombre, m.name, m.mime, m.date, m.path)
            } catch (e: Exception) {
                destino.delete()
            }
        }
        if (hechos.isNotEmpty()) guardar(c, items(c) + hechos.map { it.second })
        return hechos
    }

    /** Si Android no dejó borrar los originales, deshacemos la copia. */
    fun deshacer(c: Context, items: List<VItem>) {
        items.forEach { archivo(c, it).delete() }
        val nombres = items.map { it.file }.toSet()
        guardar(c, items(c).filter { it.file !in nombres })
    }

    /** Devuelve archivos a la galería normal (a su carpeta original si se puede). */
    fun sacar(c: Context, sel: List<VItem>): Int {
        var ok = 0
        val sacados = mutableSetOf<String>()
        for (it in sel) {
            val video = it.video
            val coleccion = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val ruta = if (Repo.destinoValido(it.path)) it.path else "Pictures/Recuperadas/"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, it.name.ifBlank { it.file })
                put(MediaStore.MediaColumns.MIME_TYPE, it.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, ruta)
                put(MediaStore.MediaColumns.DATE_TAKEN, it.date)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri: Uri = c.contentResolver.insert(coleccion, values) ?: continue
            try {
                c.contentResolver.openOutputStream(uri)?.use { out -> archivo(c, it).inputStream().use { inp -> inp.copyTo(out) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                c.contentResolver.update(uri, values, null, null)
                archivo(c, it).delete()
                sacados += it.file
                ok++
            } catch (e: Exception) {
                c.contentResolver.delete(uri, null, null)
            }
        }
        guardar(c, items(c).filter { it.file !in sacados })
        return ok
    }

    fun eliminar(c: Context, sel: List<VItem>) {
        sel.forEach { archivo(c, it).delete() }
        val nombres = sel.map { it.file }.toSet()
        guardar(c, items(c).filter { it.file !in nombres })
    }

    fun miniatura(c: Context, item: VItem): Bitmap? = try {
        val f = archivo(c, item)
        if (item.video) ThumbnailUtils.createVideoThumbnail(f, Size(320, 320), null)
        else ThumbnailUtils.createImageThumbnail(f, Size(320, 320), null)
    } catch (e: Exception) {
        null
    }

    // ---------- Respaldo cifrado ----------
    // Formato: "BAUL1" + salt(16) + iv(16) + [AES-CBC: "BAULOK" + ZIP con los archivos]

    private val MAGIA = "BAUL1".toByteArray()
    private val CONTROL = "BAULOK".toByteArray()

    private fun cifrador(modo: Int, pin: String, salt: ByteArray, iv: ByteArray): Cipher {
        val key = SecretKeySpec(derivar(pin, salt), "AES")
        return Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(modo, key, IvParameterSpec(iv)) }
    }

    fun respaldar(c: Context, pin: String, out: OutputStream) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        out.write(MAGIA)
        out.write(salt)
        out.write(iv)
        val cos = CipherOutputStream(out, cifrador(Cipher.ENCRYPT_MODE, pin, salt, iv))
        cos.write(CONTROL)
        val zip = ZipOutputStream(cos)
        val todos = items(c)
        zip.putNextEntry(ZipEntry("indice.json"))
        zip.write(indice(c).readBytes())
        zip.closeEntry()
        for (it in todos) {
            zip.putNextEntry(ZipEntry(it.file))
            archivo(c, it).inputStream().use { inp -> inp.copyTo(zip) }
            zip.closeEntry()
        }
        zip.close() // cierra también el cifrado
    }

    /** Devuelve cuántos archivos se recuperaron, o -1 si la clave no corresponde. */
    fun restaurar(c: Context, pin: String, input: InputStream): Int {
        val magia = ByteArray(MAGIA.size)
        if (input.readNBytesCompat(magia) != magia.size || !magia.contentEquals(MAGIA)) return -2
        val salt = ByteArray(16)
        val iv = ByteArray(16)
        input.readNBytesCompat(salt)
        input.readNBytesCompat(iv)
        val cis = CipherInputStream(input, cifrador(Cipher.DECRYPT_MODE, pin, salt, iv))
        val control = ByteArray(CONTROL.size)
        try {
            if (cis.readNBytesCompat(control) != control.size || !control.contentEquals(CONTROL)) return -1
        } catch (e: Exception) {
            return -1
        }
        val zip = ZipInputStream(cis)
        var nuevos: List<VItem> = emptyList()
        var n = 0
        while (true) {
            val e = zip.nextEntry ?: break
            val nombre = File(e.name).name // evita rutas raras dentro del zip
            if (nombre == "indice.json") {
                val arr = JSONArray(String(zip.readBytes()))
                nuevos = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    VItem(o.getString("file"), o.optString("name"), o.optString("mime"), o.optLong("date"), o.optString("path"))
                }
            } else {
                File(dir(c), nombre).outputStream().use { zip.copyTo(it) }
                n++
            }
            zip.closeEntry()
        }
        zip.close()
        val actuales = items(c)
        val existentes = actuales.map { it.file }.toSet()
        guardar(c, actuales + nuevos.filter { it.file !in existentes })
        return n
    }
}

/** Lee exactamente buf.size bytes (o menos si se acaba el archivo). */
private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
        val r = read(buf, total, buf.size - total)
        if (r < 0) break
        total += r
    }
    return total
}
