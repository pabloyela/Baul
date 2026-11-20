package com.tua.baul

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ---------- Modelo de la edición ----------

/** Recorte en coordenadas 0..1 de la imagen ya girada/enderezada. */
data class Recorte(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f)

data class Geo(
    val rot: Int = 0,          // giros de 90° (0..3)
    val flip: Boolean = false, // espejo horizontal
    val angulo: Float = 0f,    // enderezar (-45..45)
    val recorte: Recorte = Recorte()
)

data class AjustesFoto(
    val exposicion: Float = 0f,
    val brillo: Float = 0f,
    val contraste: Float = 0f,
    val saturacion: Float = 0f,
    val calidez: Float = 0f,
    val luces: Float = 0f,
    val sombras: Float = 0f,
    val vineta: Float = 0f
) {
    val neutro get() = this == AjustesFoto()
}

data class Edicion(
    val geo: Geo = Geo(),
    val aj: AjustesFoto = AjustesFoto(),
    val estilo: Estilo? = null,
    val estiloAj: Ajustes = Ajustes()
)

object Edit {

    /** Gira, voltea y endereza (sin recortar). */
    fun geometria(src: Bitmap, g: Geo): Bitmap {
        var b = src
        if (g.rot % 4 != 0 || g.flip) {
            val m = Matrix()
            m.postRotate(90f * (g.rot % 4))
            if (g.flip) m.postScale(-1f, 1f)
            b = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        if (abs(g.angulo) > 0.05f) {
            val w = b.width
            val h = b.height
            val rad = abs(g.angulo) * PI.toFloat() / 180f
            // escala para que no queden esquinas vacías
            val s = cos(rad) + max(w, h).toFloat() / min(w, h) * sin(rad)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            cv.translate(w / 2f, h / 2f)
            cv.rotate(g.angulo)
            cv.scale(s, s)
            cv.drawBitmap(b, -w / 2f, -h / 2f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            b = out
        }
        return b
    }

    fun recortar(b: Bitmap, r: Recorte): Bitmap {
        if (r == Recorte()) return b
        val x = (r.l * b.width).roundToInt().coerceIn(0, b.width - 1)
        val y = (r.t * b.height).roundToInt().coerceIn(0, b.height - 1)
        val w = ((r.r - r.l) * b.width).roundToInt().coerceIn(1, b.width - x)
        val h = ((r.b - r.t) * b.height).roundToInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    /** Ajustes de luz y color (en todos los núcleos). */
    suspend fun ajustar(src: Bitmap, a: AjustesFoto): Bitmap = withContext(Dispatchers.Default) {
        if (a.neutro) return@withContext src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val expo = 2f.pow(a.exposicion * 1.5f)
        val chunks = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rows = (h + chunks - 1) / chunks
        coroutineScope {
            (0 until chunks).map { c ->
                async {
                    for (yy in c * rows until min(h, (c + 1) * rows)) {
                        val dy = (yy + 0.5f) / h - 0.5f
                        var i = yy * w
                        for (xx in 0 until w) {
                            val p = px[i]
                            var r = ((p shr 16) and 0xFF) / 255f
                            var g = ((p shr 8) and 0xFF) / 255f
                            var b = (p and 0xFF) / 255f
                            r *= expo; g *= expo; b *= expo
                            val br = a.brillo * 0.2f
                            r += br; g += br; b += br
                            val k = 1f + a.contraste * 0.8f
                            r = (r - 0.5f) * k + 0.5f
                            g = (g - 0.5f) * k + 0.5f
                            b = (b - 0.5f) * k + 0.5f
                            if (a.luces != 0f || a.sombras != 0f) {
                                val l = (r * 0.2126f + g * 0.7152f + b * 0.0722f).coerceIn(0f, 1f)
                                val d = a.sombras * 0.35f * (1f - l) * (1f - l) + a.luces * 0.35f * l * l
                                r += d; g += d; b += d
                            }
                            if (a.saturacion != 0f) {
                                val l = r * 0.2126f + g * 0.7152f + b * 0.0722f
                                val s = 1f + a.saturacion
                                r = l + (r - l) * s
                                g = l + (g - l) * s
                                b = l + (b - l) * s
                            }
                            if (a.calidez != 0f) {
                                r += a.calidez * 0.08f
                                b -= a.calidez * 0.08f
                            }
                            if (a.vineta > 0f) {
                                val dx = (xx + 0.5f) / w - 0.5f
                                val d = sqrt(dx * dx + dy * dy)
                                val t = ((d - 0.3f) / 0.45f).coerceIn(0f, 1f)
                                val f = 1f - a.vineta * 0.85f * t * t * (3f - 2f * t)
                                r *= f; g *= f; b *= f
                            }
                            val ir = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val ig = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val ib = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            px[i] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
                            i++
                        }
                    }
                }
            }.awaitAll()
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        out
    }

    /** Toda la edición: geometría → recorte → ajustes → filtro. */
    suspend fun aplicar(src: Bitmap, e: Edicion): Bitmap {
        val geo = withContext(Dispatchers.Default) { geometria(src, e.geo) }
        val rec = withContext(Dispatchers.Default) { recortar(geo, e.geo.recorte) }
        val aj = ajustar(rec, e.aj)
        val est = e.estilo
        return if (est != null) Fx.apply(aj, FxUniforms.from(est, e.estiloAj)) else aj
    }
}

// ---------- Proporciones de recorte ----------

private data class Proporcion(val nombre: String, val valor: Float?) // ancho/alto; null = libre

private val proporciones = listOf(
    Proporcion("Libre", null),
    Proporcion("Original", -1f),
    Proporcion("1:1", 1f),
    Proporcion("4:3", 4f / 3f),
    Proporcion("3:4", 3f / 4f),
    Proporcion("16:9", 16f / 9f),
    Proporcion("9:16", 9f / 16f)
)

/** Recorte centrado lo más grande posible con la proporción pedida. */
private fun recorteCentrado(imgW: Int, imgH: Int, ratio: Float): Recorte {
    val imgRatio = imgW.toFloat() / imgH
    return if (imgRatio > ratio) {
        val cw = ratio / imgRatio
        Recorte((1f - cw) / 2f, 0f, (1f + cw) / 2f, 1f)
    } else {
        val ch = imgRatio / ratio
        Recorte(0f, (1f - ch) / 2f, 1f, (1f + ch) / 2f)
    }
}

// ---------- Pantalla del editor ----------

private enum class Pestana(val titulo: String) { RECORTAR("Recortar"), AJUSTES("Ajustes"), FILTROS("Filtros") }

@Composable
fun EditorScreen(a: MainActivity, media: Media) {
    var ed by remember { mutableStateOf(Edicion()) }
    var pestana by remember { mutableStateOf(Pestana.RECORTAR) }
    var proporcion by remember { mutableIntStateOf(0) }

    BackHandler { a.atras() }

    // Imagen base reducida para trabajar rápido
    val base by produceState<Bitmap?>(null, media.uri) {
        value = withContext(Dispatchers.IO) { a.decodificar(media.uri, 1600) }
    }
    // Geometría (giro/volteo/enderezar) para la vista de recorte
    val geo by produceState<Bitmap?>(null, base, ed.geo.rot, ed.geo.flip, ed.geo.angulo) {
        val b = base
        if (b != null) {
            delay(30)
            value = withContext(Dispatchers.Default) { Edit.geometria(b, ed.geo.copy(recorte = Recorte())) }
        }
    }
    // Resultado final (recortado, ajustado y con filtro)
    val vista by produceState<Bitmap?>(null, geo, ed) {
        val g = geo
        if (g != null) {
            delay(60)
            val rec = withContext(Dispatchers.Default) { Edit.recortar(g, ed.geo.recorte) }
            val aj = Edit.ajustar(rec, ed.aj)
            val est = ed.estilo
            value = if (est != null) Fx.apply(aj, FxUniforms.from(est, ed.estiloAj)) else aj
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Barra superior
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).clickable { a.atras() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White, modifier = Modifier.size(28.dp)) }
            Text(
                "Editar", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Button(
                onClick = { a.guardarEdicion(media, ed) },
                enabled = base != null && a.guardando.intValue == 0,
                colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.Black)
            ) {
                if (a.guardando.intValue > 0) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Guardar copia", fontSize = 15.sp)
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        // Imagen
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val g = geo
            if (pestana == Pestana.RECORTAR && g != null) {
                val ratio = proporciones[proporcion].valor?.let { if (it < 0f) g.width.toFloat() / g.height else it }
                VistaRecorte(g, ed.geo.recorte, ratio) { nuevo ->
                    ed = ed.copy(geo = ed.geo.copy(recorte = nuevo))
                }
            } else {
                val img = vista ?: base
                if (img != null) {
                    Image(img.asImageBitmap(), "Vista previa", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            if (base == null) CircularProgressIndicator(color = Color.White)
        }

        // Pestañas
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Pestana.entries.forEach { p ->
                val sel = p == pestana
                Text(
                    p.titulo,
                    color = if (sel) Acento else Suave,
                    fontSize = 16.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { pestana = p }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Herramientas de cada pestaña
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            when (pestana) {
                Pestana.RECORTAR -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(proporciones.size) { i ->
                            Pildora(proporciones[i].nombre, i == proporcion) {
                                proporcion = i
                                val g = geo
                                val v = proporciones[i].valor
                                if (g != null) {
                                    val nuevo = when {
                                        v == null -> ed.geo.recorte
                                        v < 0f -> Recorte()
                                        else -> recorteCentrado(g.width, g.height, v)
                                    }
                                    ed = ed.copy(geo = ed.geo.copy(recorte = nuevo))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Herramienta(Icons.Rounded.RotateRight, "Girar") {
                            ed = ed.copy(geo = ed.geo.copy(rot = (ed.geo.rot + 1) % 4, recorte = Recorte()))
                            proporcion = 0
                        }
                        Herramienta(Icons.Rounded.Flip, "Voltear") {
                            ed = ed.copy(geo = ed.geo.copy(flip = !ed.geo.flip))
                        }
                        Herramienta(Icons.Rounded.RestartAlt, "Restablecer") {
                            ed = ed.copy(geo = Geo())
                            proporcion = 0
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Deslizador("Enderezar", ed.geo.angulo, -45f..45f, formato = { "${it.roundToInt()}°" }) {
                        ed = ed.copy(geo = ed.geo.copy(angulo = it))
                    }
                }

                Pestana.AJUSTES -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
                ) {
                    val aj = ed.aj
                    Deslizador("Exposición", aj.exposicion, -1f..1f) { ed = ed.copy(aj = aj.copy(exposicion = it)) }
                    Deslizador("Brillo", aj.brillo, -1f..1f) { ed = ed.copy(aj = aj.copy(brillo = it)) }
                    Deslizador("Contraste", aj.contraste, -1f..1f) { ed = ed.copy(aj = aj.copy(contraste = it)) }
                    Deslizador("Saturación", aj.saturacion, -1f..1f) { ed = ed.copy(aj = aj.copy(saturacion = it)) }
                    Deslizador("Calidez", aj.calidez, -1f..1f) { ed = ed.copy(aj = aj.copy(calidez = it)) }
                    Deslizador("Luces", aj.luces, -1f..1f) { ed = ed.copy(aj = aj.copy(luces = it)) }
                    Deslizador("Sombras", aj.sombras, -1f..1f) { ed = ed.copy(aj = aj.copy(sombras = it)) }
                    Deslizador("Viñeta", aj.vineta, 0f..1f) { ed = ed.copy(aj = aj.copy(vineta = it)) }
                    Row {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Restablecer", color = Acento, fontSize = 15.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .clickable { ed = ed.copy(aj = AjustesFoto()) }.padding(10.dp)
                        )
                    }
                }

                Pestana.FILTROS -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        item {
                            Pildora("Ninguno", ed.estilo == null) { ed = ed.copy(estilo = null) }
                        }
                        items(Estilo.entries) { e ->
                            Pildora(e.nombre, ed.estilo == e) {
                                ed = ed.copy(estilo = e, estiloAj = Ajustes.de(e, ed.estiloAj))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (ed.estilo != null) {
                        val ea = ed.estiloAj
                        Deslizador("Intensidad", ea.intensidad, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(intensidad = it)) }
                        Deslizador("Rojos", ea.rojos, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(rojos = it)) }
                        Deslizador("Grano", ea.grano, 0f..1f) { ed = ed.copy(estiloAj = ea.copy(grano = it)) }
                    } else {
                        Text(
                            "Elige un estilo de Claroscuro: Dramático, Cianotipo, Noir…",
                            color = Suave, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------- Vista de recorte con esquinas arrastrables ----------

private enum class Asa { NADA, MOVER, SI, SD, II, ID } // superior/inferior, izquierda/derecha

@Composable
private fun VistaRecorte(img: Bitmap, rec: Recorte, ratio: Float?, onCambio: (Recorte) -> Unit) {
    val density = LocalDensity.current
    val tolPx = with(density) { 36.dp.toPx() }
    var asa by remember { mutableStateOf(Asa.NADA) }
    var actual by remember(rec) { mutableStateOf(rec) }
    val imagen = remember(img) { img.asImageBitmap() }

    // Dónde se dibuja la imagen dentro del área (ajustada sin deformar)
    fun destino(area: Size): Pair<Offset, Size> {
        val s = min(area.width / img.width, area.height / img.height)
        val w = img.width * s
        val h = img.height * s
        return Offset((area.width - w) / 2f, (area.height - h) / 2f) to Size(w, h)
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(img, ratio) {
                detectDragGestures(
                    onDragStart = { p ->
                        val (o, s) = destino(Size(size.width.toFloat(), size.height.toFloat()))
                        val r = actual
                        val x0 = o.x + r.l * s.width
                        val x1 = o.x + r.r * s.width
                        val y0 = o.y + r.t * s.height
                        val y1 = o.y + r.b * s.height
                        fun cerca(x: Float, y: Float) = abs(p.x - x) < tolPx && abs(p.y - y) < tolPx
                        asa = when {
                            cerca(x0, y0) -> Asa.SI
                            cerca(x1, y0) -> Asa.SD
                            cerca(x0, y1) -> Asa.II
                            cerca(x1, y1) -> Asa.ID
                            p.x in x0..x1 && p.y in y0..y1 -> Asa.MOVER
                            else -> Asa.NADA
                        }
                    },
                    onDragEnd = { asa = Asa.NADA },
                    onDragCancel = { asa = Asa.NADA },
                    onDrag = { change, d ->
                        if (asa == Asa.NADA) return@detectDragGestures
                        change.consume()
                        val (_, s) = destino(Size(size.width.toFloat(), size.height.toFloat()))
                        val dx = d.x / s.width
                        val dy = d.y / s.height
                        val r = actual
                        val minimo = 0.08f
                        var n = when (asa) {
                            Asa.MOVER -> {
                                val w = r.r - r.l
                                val h = r.b - r.t
                                val l = (r.l + dx).coerceIn(0f, 1f - w)
                                val t = (r.t + dy).coerceIn(0f, 1f - h)
                                Recorte(l, t, l + w, t + h)
                            }
                            Asa.SI -> r.copy(l = (r.l + dx).coerceIn(0f, r.r - minimo), t = (r.t + dy).coerceIn(0f, r.b - minimo))
                            Asa.SD -> r.copy(r = (r.r + dx).coerceIn(r.l + minimo, 1f), t = (r.t + dy).coerceIn(0f, r.b - minimo))
                            Asa.II -> r.copy(l = (r.l + dx).coerceIn(0f, r.r - minimo), b = (r.b + dy).coerceIn(r.t + minimo, 1f))
                            Asa.ID -> r.copy(r = (r.r + dx).coerceIn(r.l + minimo, 1f), b = (r.b + dy).coerceIn(r.t + minimo, 1f))
                            Asa.NADA -> r
                        }
                        // Proporción fija: el alto sigue al ancho, anclado en la esquina contraria
                        if (ratio != null && asa != Asa.MOVER) {
                            val k = img.width.toFloat() / img.height / ratio // alto normalizado por ancho normalizado
                            var w = n.r - n.l
                            var h = w * k
                            val arriba = asa == Asa.SI || asa == Asa.SD
                            val maxH = if (arriba) n.b else 1f - n.t
                            if (h > maxH) {
                                h = maxH
                                w = h / k
                            }
                            val izquierda = asa == Asa.SI || asa == Asa.II
                            val l = if (izquierda) n.r - w else n.l
                            val rr = if (izquierda) n.r else n.l + w
                            n = if (arriba) Recorte(l, n.b - h, rr, n.b) else Recorte(l, n.t, rr, n.t + h)
                        }
                        actual = n
                        onCambio(n)
                    }
                )
            }
    ) {
        val (o, s) = destino(size)
        drawImage(
            imagen,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
            dstSize = IntSize(s.width.roundToInt(), s.height.roundToInt())
        )
        val r = actual
        val x0 = o.x + r.l * s.width
        val x1 = o.x + r.r * s.width
        val y0 = o.y + r.t * s.height
        val y1 = o.y + r.b * s.height
        val sombra = Color(0xAA000000)
        // Oscurecer lo que queda fuera del recorte
        drawRect(sombra, Offset(o.x, o.y), Size(s.width, y0 - o.y))
        drawRect(sombra, Offset(o.x, y1), Size(s.width, o.y + s.height - y1))
        drawRect(sombra, Offset(o.x, y0), Size(x0 - o.x, y1 - y0))
        drawRect(sombra, Offset(x1, y0), Size(o.x + s.width - x1, y1 - y0))
        // Marco y cuadrícula de tercios
        drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(width = 3f))
        val guia = Color(0x66FFFFFF)
        for (i in 1..2) {
            val gx = x0 + (x1 - x0) * i / 3f
            val gy = y0 + (y1 - y0) * i / 3f
            drawLine(guia, Offset(gx, y0), Offset(gx, y1), strokeWidth = 1.5f)
            drawLine(guia, Offset(x0, gy), Offset(x1, gy), strokeWidth = 1.5f)
        }
        // Esquinas gruesas
        val L = 48f
        val e = 9f
        listOf(Offset(x0, y0) to Offset(1f, 1f), Offset(x1, y0) to Offset(-1f, 1f),
            Offset(x0, y1) to Offset(1f, -1f), Offset(x1, y1) to Offset(-1f, -1f)).forEach { (pt, dir) ->
            drawLine(Color.White, pt, Offset(pt.x + L * dir.x, pt.y), strokeWidth = e)
            drawLine(Color.White, pt, Offset(pt.x, pt.y + L * dir.y), strokeWidth = e)
        }
    }
}

// ---------- Piezas pequeñas ----------

@Composable
fun Pildora(texto: String, activo: Boolean, onClick: () -> Unit) {
    Text(
        texto,
        color = if (activo) Color.Black else Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (activo) Acento else Color(0xFF2A2A30))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun Herramienta(icon: ImageVector, texto: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, texto, tint = Color.White, modifier = Modifier.size(28.dp))
        Text(texto, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Deslizador(
    nombre: String,
    valor: Float,
    rango: ClosedFloatingPointRange<Float>,
    formato: (Float) -> String = { v ->
        val pct = (v * 100).roundToInt()
        if (rango.start < 0f && pct > 0) "+$pct" else "$pct"
    },
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(88.dp))
        Slider(
            value = valor,
            onValueChange = onChange,
            valueRange = rango,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Acento,
                activeTrackColor = Acento,
                inactiveTrackColor = Color(0x55FFFFFF)
            )
        )
        Text(formato(valor), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    }
}
