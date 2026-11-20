package com.tua.baul

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pantallas de la app. */
sealed class Pant {
    object Inicio : Pant()
    data class AlbumP(val titulo: String, val filtro: (Media) -> Boolean) : Pant()
    data class Visor(val lista: List<Media>, val inicio: Int) : Pant()
    data class Editor(val media: Media) : Pant()
    object Papelera : Pant()
    object Ajustes : Pant()
    object Boveda : Pant()
    data class VisorBoveda(val inicio: Int) : Pant()
}

class MainActivity : ComponentActivity() {

    // ---------- Estado ----------
    val permiso = mutableStateOf(false)
    val pila = mutableStateListOf<Pant>(Pant.Inicio)
    val medios = mutableStateOf<List<Media>?>(null)
    val papelera = mutableStateOf<List<Media>?>(null)
    val favoritos = mutableStateOf<Set<String>>(emptySet())
    val pestana = mutableIntStateOf(0)   // 0 = Fotos, 1 = Álbumes
    val columnas = mutableIntStateOf(4)
    val guardando = mutableIntStateOf(0)
    val bovedaAbierta = mutableStateOf(false)
    val bovedaItems = mutableStateOf<List<VItem>>(emptyList())

    private var alTerminar: ((Boolean) -> Unit)? = null
    private var respaldoPin: String = ""
    private var salidaInterna = false // abrimos Pelis o el selector de archivos nosotros mismos

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permiso.value = tienePermiso()
        if (permiso.value) recargar()
    }

    /** Resultado de los cuadros de confirmación del sistema (borrar, mover, papelera). */
    private val pedirLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val cb = alTerminar
        alTerminar = null
        cb?.invoke(r.resultCode == RESULT_OK)
        recargar()
    }

    private val exportarLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) exportarBoveda(uri) }

    private val importarLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importarBoveda(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        columnas.intValue = getSharedPreferences("ajustes", MODE_PRIVATE).getInt("columnas", 4)
        permiso.value = tienePermiso()
        favoritos.value = Favoritos.todos(this)

        setContent {
            BaulTheme { App(this) }
        }

        if (!permiso.value) pedirPermiso() else recargar()
        abrirDesdeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesdeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        salidaInterna = false
        if (permiso.value) recargar()
    }

    override fun onStop() {
        super.onStop()
        // Al salir de la app, el área privada se cierra sola
        // (salvo que la hayamos dejado nosotros para ver un video o elegir un archivo)
        if (!salidaInterna) cerrarBoveda()
    }

    // ---------- Permisos ----------

    private fun tienePermiso(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun pedirPermiso() {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permLauncher.launch(perms)
    }

    /** ¿Tiene el permiso especial para borrar/mover sin confirmar cada vez? */
    fun gestionaMedios(): Boolean = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this)

    fun abrirPermisoGestion() {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            aviso("Búscalo en Ajustes › Apps › Acceso especial › Administración de medios")
        }
    }

    // ---------- Navegación ----------

    fun ir(p: Pant) {
        pila.add(p)
    }

    fun atras() {
        if (pila.size > 1) pila.removeAt(pila.lastIndex) else finish()
        if (pila.none { it is Pant.Boveda || it is Pant.VisorBoveda }) seguridad(false)
    }

    // ---------- Datos ----------

    fun recargar() {
        lifecycleScope.launch {
            val lista = withContext(Dispatchers.IO) { Repo.load(this@MainActivity) }
            medios.value = lista
            if (pila.any { it is Pant.Papelera }) cargarPapelera()
        }
    }

    fun cargarPapelera() {
        lifecycleScope.launch {
            papelera.value = withContext(Dispatchers.IO) { Repo.load(this@MainActivity, trashed = true) }
        }
    }

    fun guardarColumnas(n: Int) {
        columnas.intValue = n
        getSharedPreferences("ajustes", MODE_PRIVATE).edit().putInt("columnas", n).apply()
    }

    fun aviso(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun pedir(pi: PendingIntent, despues: (Boolean) -> Unit) {
        alTerminar = despues
        pedirLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    // ---------- Acciones sobre fotos ----------

    fun compartir(lista: List<Media>) {
        if (lista.isEmpty()) return
        val intent = if (lista.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = lista[0].mime
                putExtra(Intent.EXTRA_STREAM, lista[0].uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (lista.all { it.video }) "video/*" else if (lista.none { it.video }) "image/*" else "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(lista.map { it.uri }))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Compartir"))
    }

    fun aPapelera(lista: List<Media>, despues: () -> Unit = {}) {
        if (lista.isEmpty()) return
        pedir(MediaStore.createTrashRequest(contentResolver, lista.map { it.uri }, true)) { ok ->
            if (ok) {
                aviso(if (lista.size == 1) "Enviada a la papelera" else "${lista.size} elementos a la papelera")
                despues()
            }
        }
    }

    fun restaurar(lista: List<Media>) {
        if (lista.isEmpty()) return
        pedir(MediaStore.createTrashRequest(contentResolver, lista.map { it.uri }, false)) { cargarPapelera() }
    }

    fun eliminarDefinitivo(lista: List<Media>) {
        if (lista.isEmpty()) return
        pedir(MediaStore.createDeleteRequest(contentResolver, lista.map { it.uri })) { cargarPapelera() }
    }

    fun alternarFavorito(lista: List<Media>) {
        if (lista.isEmpty()) return
        val marcar = !lista.all { it.uri.toString() in favoritos.value }
        Favoritos.alternar(this, lista, marcar)
        favoritos.value = Favoritos.todos(this)
    }

    /** Mueve fotos/videos a una carpeta (álbum). */
    fun mover(lista: List<Media>, carpeta: String) {
        if (lista.isEmpty()) return
        val destino = if (carpeta.endsWith("/")) carpeta else "$carpeta/"
        pedir(MediaStore.createWriteRequest(contentResolver, lista.map { it.uri })) { ok ->
            if (!ok) return@pedir
            var n = 0
            for (m in lista) {
                try {
                    val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, destino) }
                    if (contentResolver.update(m.uri, v, null, null) > 0) n++
                } catch (e: Exception) {
                    // algunas carpetas no aceptan ciertos tipos de archivo
                }
            }
            aviso(if (n == lista.size) "Movido a ${destino.trimEnd('/').substringAfterLast('/')}" else "Se movieron $n de ${lista.size}")
            recargar()
        }
    }

    fun abrirVideo(m: Media) = abrirVideoUri(m.uri, m.mime)

    private fun abrirVideoUri(uri: Uri, mime: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "video/*" })
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            aviso("No hay app para reproducir el video")
        }
    }

    // ---------- Abrir fotos que vienen de otras apps ----------

    private fun abrirDesdeIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val mime = intent.type ?: contentResolver.getType(uri) ?: "image/jpeg"
        var nombre = "Foto"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) nombre = it.getString(0) ?: nombre
            }
        } catch (e: Exception) {
            // sin nombre
        }
        val m = Media(-1, uri, esVideoMime(mime), nombre, System.currentTimeMillis(), 0, 0, 0, 0, -1, "", "", mime)
        ir(Pant.Visor(listOf(m), 0))
    }

    // ---------- Imágenes ----------

    /** Abre una foto ya derecha, con el lado largo limitado a [maxLado]. */
    fun decodificar(uri: Uri, maxLado: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val esc = minOf(1f, maxLado.toFloat() / maxOf(w, h))
            if (esc < 1f) dec.setTargetSize((w * esc).toInt().coerceAtLeast(1), (h * esc).toInt().coerceAtLeast(1))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            dec.isMutableRequired = true
        }
    } catch (e: Throwable) {
        null
    }

    fun decodificarArchivo(f: java.io.File, maxLado: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { dec, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val esc = minOf(1f, maxLado.toFloat() / maxOf(w, h))
            if (esc < 1f) dec.setTargetSize((w * esc).toInt().coerceAtLeast(1), (h * esc).toInt().coerceAtLeast(1))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Throwable) {
        null
    }

    /** Guarda la edición como una copia nueva, junto a la original. */
    fun guardarEdicion(m: Media, e: Edicion) {
        guardando.intValue++
        lifecycleScope.launch {
            try {
                val full = withContext(Dispatchers.IO) { decodificar(m.uri, 4000) }
                if (full == null) {
                    aviso("No se pudo abrir la foto")
                    return@launch
                }
                val out = Edit.aplicar(full, e)
                val carpeta = if (Repo.destinoValido(m.path)) m.path else "Pictures/Editadas/"
                val base = m.name.substringBeforeLast('.').ifBlank { "foto" }
                val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
                val uri = withContext(Dispatchers.IO) { guardarJpeg(out, "${base}_editada_$stamp.jpg", carpeta) }
                if (uri != null) {
                    aviso("Copia guardada")
                    recargar()
                    atras()
                } else {
                    aviso("No se pudo guardar")
                }
            } catch (t: Throwable) {
                aviso("No se pudo guardar")
            } finally {
                guardando.intValue--
            }
        }
    }

    private fun guardarJpeg(bmp: Bitmap, nombre: String, carpeta: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, carpeta)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } catch (ex: Exception) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    // ---------- Bóveda ----------

    /** Se llama al escribir en el buscador: si el texto es la clave, abre la bóveda. */
    fun probarClave(texto: String) {
        if (texto.length < 4 || bovedaAbierta.value || !Boveda.tieneClave(this)) return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.Default) { Boveda.comprobar(this@MainActivity, texto) }
            if (ok && !bovedaAbierta.value) {
                bovedaAbierta.value = true
                bovedaItems.value = withContext(Dispatchers.IO) { Boveda.items(this@MainActivity) }
                seguridad(true)
                ir(Pant.Boveda)
            }
        }
    }

    fun cerrarBoveda() {
        if (!bovedaAbierta.value) return
        bovedaAbierta.value = false
        bovedaItems.value = emptyList()
        while (pila.size > 1 && pila.any { it is Pant.Boveda || it is Pant.VisorBoveda }) {
            pila.removeAt(pila.lastIndex)
        }
        seguridad(false)
    }

    /** Sin capturas de pantalla ni vista en "recientes" mientras la bóveda está abierta. */
    private fun seguridad(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun ponerClave(pin: String) {
        Boveda.ponerClave(this, pin)
        aviso("Clave guardada")
    }

    fun tieneClave() = Boveda.tieneClave(this)
    fun claveCorrecta(pin: String) = Boveda.comprobar(this, pin)

    /** Mueve fotos/videos a la bóveda: los copia y luego borra los originales. */
    fun ocultar(lista: List<Media>, despues: () -> Unit = {}) {
        if (!bovedaAbierta.value || lista.isEmpty()) return
        guardando.intValue++
        lifecycleScope.launch {
            val hechos = withContext(Dispatchers.IO) { Boveda.meter(this@MainActivity, lista) }
            guardando.intValue--
            if (hechos.isEmpty()) {
                aviso("No se pudo mover")
                return@launch
            }
            pedir(MediaStore.createDeleteRequest(contentResolver, hechos.map { it.first.uri })) { ok ->
                if (ok) {
                    aviso("${hechos.size} a la bóveda")
                    despues()
                } else {
                    Boveda.deshacer(this@MainActivity, hechos.map { it.second })
                }
                bovedaItems.value = Boveda.items(this@MainActivity)
            }
        }
    }

    fun sacarDeBoveda(sel: List<VItem>) {
        guardando.intValue++
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) { Boveda.sacar(this@MainActivity, sel) }
            guardando.intValue--
            bovedaItems.value = Boveda.items(this@MainActivity)
            aviso("$n devueltos a la galería")
            recargar()
        }
    }

    fun eliminarDeBoveda(sel: List<VItem>) {
        Boveda.eliminar(this, sel)
        bovedaItems.value = Boveda.items(this)
    }

    fun abrirVideoBoveda(item: VItem) {
        salidaInterna = true
        val uri = FileProvider.getUriForFile(this, "com.tua.baul.archivos", Boveda.archivo(this, item))
        abrirVideoUri(uri, item.mime)
    }

    fun pedirRespaldo(pin: String) {
        respaldoPin = pin
        salidaInterna = true
        val fecha = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        exportarLauncher.launch("boveda_$fecha.baul")
    }

    fun pedirImportar(pin: String) {
        respaldoPin = pin
        salidaInterna = true
        importarLauncher.launch(arrayOf("*/*"))
    }

    private fun exportarBoveda(uri: Uri) {
        val pin = respaldoPin
        guardando.intValue++
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { Boveda.respaldar(this@MainActivity, pin, it) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            guardando.intValue--
            aviso(if (ok) "Respaldo guardado. Cópialo a tu computadora." else "No se pudo crear el respaldo")
        }
    }

    private fun importarBoveda(uri: Uri) {
        val pin = respaldoPin
        guardando.intValue++
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { Boveda.restaurar(this@MainActivity, pin, it) } ?: -3
                } catch (e: Exception) {
                    -3
                }
            }
            guardando.intValue--
            bovedaItems.value = Boveda.items(this@MainActivity)
            aviso(
                when (n) {
                    -1 -> "La clave no corresponde a ese respaldo"
                    -2 -> "Ese archivo no es un respaldo de Baúl"
                    -3 -> "No se pudo leer el respaldo"
                    else -> "$n archivos recuperados"
                }
            )
        }
    }

    @Suppress("unused")
    private fun ctx(): Context = this
}
