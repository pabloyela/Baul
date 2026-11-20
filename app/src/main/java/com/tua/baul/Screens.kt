package com.tua.baul

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

// ======================================================================
// Raíz
// ======================================================================

@Composable
fun App(a: MainActivity) {
    Surface(color = Fondo, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        if (!a.permiso.value) PermisoScreen(a)
        else when (val p = a.pila.last()) {
            is Pant.Inicio -> InicioScreen(a)
            is Pant.AlbumP -> AlbumScreen(a, p.titulo, p.filtro)
            is Pant.Visor -> VisorScreen(a, p.lista, p.inicio)
            is Pant.Editor -> EditorScreen(a, p.media)
            is Pant.Papelera -> PapeleraScreen(a)
            is Pant.Ajustes -> AjustesScreen(a)
            is Pant.Boveda -> BovedaScreen(a)
            is Pant.VisorBoveda -> VisorBovedaScreen(a, p.inicio)
            is Pant.Reproductor -> ReproductorScreen(a, p.uri, p.titulo)
        }
    }
}

@Composable
private fun PermisoScreen(a: MainActivity) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.PhotoLibrary, null, tint = Acento, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text("Baúl necesita permiso para ver tus fotos y videos.", fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { a.pedirPermiso() },
            colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) { Text("Dar permiso", fontSize = 18.sp) }
        Spacer(Modifier.height(16.dp))
        Text(
            "Elige \"Permitir todo\". Si ya lo negaste: Ajustes › Apps › Baúl › Permisos.",
            color = Suave, fontSize = 14.sp, textAlign = TextAlign.Center
        )
    }
}

// ======================================================================
// Inicio: Fotos y Álbumes
// ======================================================================

private enum class Filtro(val nombre: String) { TODO("Todo"), FOTOS("Fotos"), VIDEOS("Videos"), FAVORITOS("Favoritos"), CAPTURAS("Capturas") }

@Composable
private fun InicioScreen(a: MainActivity) {
    val todos = a.medios.value
    var sel by remember { mutableStateOf(setOf<Long>()) }
    var filtro by remember { mutableStateOf(Filtro.TODO) }
    var buscando by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    val favs = a.favoritos.value

    BackHandler(enabled = sel.isNotEmpty() || buscando) {
        if (sel.isNotEmpty()) sel = emptySet() else { buscando = false; texto = "" }
    }

    // La clave escrita en el buscador abre el área privada
    LaunchedEffect(texto) {
        if (texto.length >= 4) {
            delay(250)
            a.probarClave(texto)
        }
    }
    LaunchedEffect(a.bovedaAbierta.value) {
        if (a.bovedaAbierta.value) {
            texto = ""
            buscando = false
        }
    }

    val lista = remember(todos, filtro, texto, favs) {
        (todos ?: emptyList()).filter { m ->
            val pasa = when (filtro) {
                Filtro.TODO -> true
                Filtro.FOTOS -> !m.video
                Filtro.VIDEOS -> m.video
                Filtro.FAVORITOS -> m.uri.toString() in favs
                Filtro.CAPTURAS -> Repo.esCaptura(m)
            }
            pasa && (texto.isBlank() || m.name.contains(texto, ignoreCase = true) || m.bucket.contains(texto, ignoreCase = true))
        }
    }
    val elegidos = remember(lista, sel) { lista.filter { it.id in sel } }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (sel.isNotEmpty()) {
            CabeceraSeleccion(sel.size, onCerrar = { sel = emptySet() }, onTodo = { sel = lista.map { it.id }.toSet() })
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Baúl", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconoBoton(Icons.Rounded.Search, "Buscar") { buscando = !buscando; if (!buscando) texto = "" }
                IconoBoton(Icons.Rounded.Settings, "Ajustes") { a.ir(Pant.Ajustes) }
            }
        }
        if (buscando && sel.isEmpty()) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
                placeholder = { Text("Buscar por nombre o álbum") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Tarjeta, unfocusedContainerColor = Tarjeta,
                    focusedIndicatorColor = Acento
                )
            )
        }

        Box(Modifier.weight(1f)) {
            if (todos == null) {
                CircularProgressIndicator(color = Acento, modifier = Modifier.align(Alignment.Center))
            } else if (a.pestana.intValue == 0) {
                MediaGrid(a, lista, sel, { sel = it }, arriba = {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(Filtro.entries) { f -> Pildora(f.nombre, f == filtro) { filtro = f; sel = emptySet() } }
                    }
                })
            } else {
                AlbumesGrid(a, todos)
            }
        }

        if (sel.isNotEmpty()) {
            BarraSeleccion(a, elegidos) { sel = emptySet() }
        } else {
            Row(Modifier.fillMaxWidth().background(Tarjeta).padding(vertical = 4.dp)) {
                PestanaInferior(Icons.Rounded.Photo, "Fotos", a.pestana.intValue == 0) { a.pestana.intValue = 0 }
                PestanaInferior(Icons.Rounded.Folder, "Álbumes", a.pestana.intValue == 1) { a.pestana.intValue = 1 }
            }
        }
    }
}

@Composable
private fun RowScope.PestanaInferior(icon: ImageVector, texto: String, activo: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, texto, tint = if (activo) Acento else Suave, modifier = Modifier.size(26.dp))
        Text(texto, color = if (activo) Acento else Suave, fontSize = 13.sp, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

// ======================================================================
// Cuadrícula con selección cómoda
// ======================================================================

private sealed class Celda {
    data class Cabecera(val dia: LocalDate, val ids: List<Long>) : Celda()
    data class Item(val m: Media) : Celda()
}

/**
 * Cuadrícula de fotos:
 * - Toque: abre (o, si estás seleccionando, marca/desmarca tocando CUALQUIER parte).
 * - Mantener presionado y arrastrar: selecciona muchas de un solo trazo.
 * - Pellizcar: cambia el tamaño de las miniaturas.
 */
@Composable
fun MediaGrid(
    a: MainActivity,
    lista: List<Media>,
    sel: Set<Long>,
    onSel: (Set<Long>) -> Unit,
    agrupar: Boolean = true,
    arriba: (@Composable () -> Unit)? = null
) {
    val cols = a.columnas.intValue
    val celdas = remember(lista, agrupar) {
        if (!agrupar) lista.map { Celda.Item(it) }
        else {
            val out = mutableListOf<Celda>()
            lista.groupBy { diaDe(it.date) }.forEach { (dia, ms) ->
                out += Celda.Cabecera(dia, ms.map { it.id })
                ms.forEach { out += Celda.Item(it) }
            }
            out
        }
    }
    val state = rememberLazyGridState()
    val selActual by rememberUpdatedState(sel)
    val onSelActual by rememberUpdatedState(onSel)
    val listaActual by rememberUpdatedState(lista)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val densidad = LocalDensity.current
    val bordePx = with(densidad) { 80.dp.toPx() }
    var ignorarToque by remember { mutableStateOf(false) }
    val favs = a.favoritos.value

    fun idEn(pos: Offset): Long? = state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        pos.x >= info.offset.x && pos.x < info.offset.x + info.size.width &&
            pos.y >= info.offset.y && pos.y < info.offset.y + info.size.height
    }?.key as? Long

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = state,
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            // Cada gesto empieza "limpio"
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    ignorarToque = false
                }
            }
            // Pellizcar para cambiar columnas (solo con dos dedos)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var acumulado = 1f
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.count { it.pressed } >= 2) {
                            acumulado *= ev.calculateZoom()
                            val c = a.columnas.intValue
                            if (acumulado > 1.3f && c > 2) {
                                a.guardarColumnas(c - 1); acumulado = 1f
                            } else if (acumulado < 0.77f && c < 6) {
                                a.guardarColumnas(c + 1); acumulado = 1f
                            }
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
            // Toque
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    if (ignorarToque) return@detectTapGestures
                    val id = idEn(pos) ?: return@detectTapGestures
                    val s = selActual
                    if (s.isNotEmpty()) {
                        onSelActual(if (id in s) s - id else s + id)
                    } else {
                        val l = listaActual
                        val i = l.indexOfFirst { it.id == id }
                        if (i >= 0) a.ir(Pant.Visor(l, i))
                    }
                })
            }
            // Mantener presionado + arrastrar = seleccionar varias
            .pointerInput(Unit) {
                var ancla = -1
                var base = emptySet<Long>()
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        val id = idEn(pos) ?: return@detectDragGesturesAfterLongPress
                        ignorarToque = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        ancla = listaActual.indexOfFirst { it.id == id }
                        base = selActual
                        onSelActual(base + id)
                    },
                    onDragEnd = { ancla = -1 },
                    onDragCancel = { ancla = -1 },
                    onDrag = { change, _ ->
                        if (ancla < 0) return@detectDragGesturesAfterLongPress
                        change.consume()
                        val id = idEn(change.position)
                        if (id != null) {
                            val l = listaActual
                            val i = l.indexOfFirst { it.id == id }
                            if (i >= 0) {
                                val rango = if (i >= ancla) ancla..i else i..ancla
                                onSelActual(base + rango.map { l[it].id })
                            }
                        }
                        // Desplazamiento automático cerca de los bordes
                        val y = change.position.y
                        if (y > size.height - bordePx) scope.launch { state.scrollBy(28f) }
                        else if (y < bordePx) scope.launch { state.scrollBy(-28f) }
                    }
                )
            }
    ) {
        if (arriba != null) {
            item(key = "arriba", span = { GridItemSpan(maxLineSpan) }) { arriba() }
        }
        if (lista.isEmpty()) {
            item(key = "vacio", span = { GridItemSpan(maxLineSpan) }) {
                Text("Nada por aquí.", color = Suave, fontSize = 16.sp, modifier = Modifier.padding(24.dp))
            }
        }
        celdas.forEach { c ->
            when (c) {
                is Celda.Cabecera -> item(key = "d" + c.dia.toString(), span = { GridItemSpan(maxLineSpan) }) {
                    val todosDia = c.ids.all { it in sel }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(etiquetaDia(c.dia), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        // Seleccionar todo el día de un toque
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable {
                                onSel(if (todosDia) sel - c.ids.toSet() else sel + c.ids)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (todosDia) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                "Seleccionar el día",
                                tint = if (todosDia) Acento else Color(0x66FFFFFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                is Celda.Item -> item(key = c.m.id) {
                    CeldaMedia(c.m, c.m.id in sel, sel.isNotEmpty(), c.m.uri.toString() in favs)
                }
            }
        }
    }
}

@Composable
private fun CeldaMedia(m: Media, seleccionado: Boolean, seleccionando: Boolean, favorito: Boolean) {
    Box(Modifier.aspectRatio(1f).padding(1.dp)) {
        Miniatura(m.uri, Modifier.fillMaxSize())
        if (m.video) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(formatTime(m.duration), color = Color.White, fontSize = 11.sp)
            }
        }
        if (favorito) {
            Icon(
                Icons.Rounded.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp)
            )
        }
        if (seleccionado) {
            Box(Modifier.fillMaxSize().background(Acento.copy(alpha = 0.28f)).border(3.dp, Acento))
        }
        if (seleccionando) {
            Icon(
                if (seleccionado) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (seleccionado) Acento else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp)
                    .clip(CircleShape).background(if (seleccionado) Color.Black else Color(0x55000000))
            )
        }
    }
}

@Composable
private fun CabeceraSeleccion(n: Int, onCerrar: () -> Unit, onTodo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoBoton(Icons.Rounded.Close, "Cancelar", onCerrar)
        Text(
            if (n == 1) "1 seleccionado" else "$n seleccionados",
            color = Acento, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        TextButton(onClick = onTodo) { Text("Todo", color = Color.White, fontSize = 16.sp) }
    }
}

@Composable
private fun BarraSeleccion(a: MainActivity, elegidos: List<Media>, limpiar: () -> Unit) {
    var mover by remember { mutableStateOf(false) }
    val favs = a.favoritos.value
    val todosFav = elegidos.isNotEmpty() && elegidos.all { it.uri.toString() in favs }
    Row(Modifier.fillMaxWidth().background(Tarjeta).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Accion(Icons.Rounded.Share, "Compartir") { a.compartir(elegidos) }
        Accion(Icons.Rounded.DriveFileMove, "Mover") { mover = true }
        Accion(if (todosFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito") {
            a.alternarFavorito(elegidos)
        }
        if (a.bovedaAbierta.value) {
            Accion(Icons.Rounded.Lock, "Privado") { a.ocultar(elegidos) { limpiar() } }
        }
        Accion(Icons.Rounded.Delete, "Papelera", Color(0xFFFF6B6B)) { a.aPapelera(elegidos) { limpiar() } }
    }
    if (mover) {
        MoverDialog(a, onElegir = { carpeta ->
            mover = false
            a.mover(elegidos, carpeta)
            limpiar()
        }, onCerrar = { mover = false })
    }
}

@Composable
fun RowScope.Accion(icon: ImageVector, texto: String, color: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, texto, tint = color, modifier = Modifier.size(26.dp))
        Text(texto, color = color, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun IconoBoton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun MoverDialog(a: MainActivity, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val albums = remember(a.medios.value) {
        Repo.albums(a.medios.value ?: emptyList()).filter { Repo.destinoValido(it.path) }
    }
    var nuevo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Mover a…") },
        text = {
            Column {
                OutlinedTextField(
                    value = nuevo,
                    onValueChange = { nuevo = it.replace("/", "").take(40) },
                    singleLine = true,
                    placeholder = { Text("Nuevo álbum") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (nuevo.isNotBlank()) {
                    TextButton(onClick = { onElegir("Pictures/${nuevo.trim()}/") }) {
                        Text("Crear «${nuevo.trim()}» y mover", color = Acento)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(albums) { al ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onElegir(al.path) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Miniatura(al.items.first().uri, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), 160)
                            Text(al.nombre, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = Suave) } }
    )
}

// ======================================================================
// Álbumes
// ======================================================================

@Composable
private fun AlbumesGrid(a: MainActivity, todos: List<Media>) {
    val albums = remember(todos) { Repo.albums(todos) }
    val favs = a.favoritos.value
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val favoritos = todos.filter { it.uri.toString() in favs }
        if (favoritos.isNotEmpty()) {
            item(key = "fav") {
                TarjetaAlbum("Favoritos", favoritos.size, favoritos.first().uri, Icons.Rounded.Favorite) {
                    a.ir(Pant.AlbumP("Favoritos") { m -> m.uri.toString() in a.favoritos.value })
                }
            }
        }
        val videos = todos.filter { it.video }
        if (videos.isNotEmpty()) {
            item(key = "vid") {
                TarjetaAlbum("Videos", videos.size, videos.first().uri, Icons.Rounded.Videocam) {
                    a.ir(Pant.AlbumP("Videos") { it.video })
                }
            }
        }
        items(albums.size, key = { albums[it].id }) { i ->
            val al = albums[i]
            TarjetaAlbum(al.nombre, al.items.size, al.items.first().uri, null) {
                val id = al.id
                a.ir(Pant.AlbumP(al.nombre) { it.bucketId == id })
            }
        }
        item(key = "papelera") {
            TarjetaAlbum("Papelera", -1, null, Icons.Rounded.Delete) { a.ir(Pant.Papelera) }
        }
    }
}

@Composable
private fun TarjetaAlbum(nombre: String, n: Int, portada: Uri?, icono: ImageVector?, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Tarjeta)) {
            if (portada != null) Miniatura(portada, Modifier.fillMaxSize(), 400)
            if (icono != null) {
                Box(
                    Modifier.align(if (portada == null) Alignment.Center else Alignment.TopStart).padding(8.dp)
                        .size(34.dp).clip(CircleShape).background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center
                ) { Icon(icono, null, tint = Acento, modifier = Modifier.size(20.dp)) }
            }
        }
        Text(nombre, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        if (n >= 0) Text("$n", fontSize = 13.sp, color = Suave, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun AlbumScreen(a: MainActivity, titulo: String, filtro: (Media) -> Boolean) {
    val todos = a.medios.value ?: emptyList()
    val lista = remember(todos, a.favoritos.value) { todos.filter(filtro) }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val elegidos = remember(lista, sel) { lista.filter { it.id in sel } }
    BackHandler { if (sel.isNotEmpty()) sel = emptySet() else a.atras() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (sel.isNotEmpty()) {
            CabeceraSeleccion(sel.size, { sel = emptySet() }, { sel = lista.map { it.id }.toSet() })
        } else {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${lista.size} elementos", fontSize = 13.sp, color = Suave)
                }
            }
        }
        Box(Modifier.weight(1f)) { MediaGrid(a, lista, sel, { sel = it }) }
        if (sel.isNotEmpty()) BarraSeleccion(a, elegidos) { sel = emptySet() }
    }
}

// ======================================================================
// Visor con zoom
// ======================================================================

@Composable
private fun VisorScreen(a: MainActivity, lista0: List<Media>, inicio: Int) {
    // Si algo se borró, seguimos con la lista actualizada
    val vivos = a.medios.value
    val lista = remember(lista0, vivos) {
        if (vivos == null || lista0.firstOrNull()?.id == -1L) lista0
        else {
            val ids = vivos.map { it.id }.toSet()
            lista0.filter { it.id in ids }
        }
    }
    if (lista.isEmpty()) {
        LaunchedEffect(Unit) { a.atras() }
        return
    }
    val pager = rememberPagerState(initialPage = inicio.coerceIn(0, lista.size - 1)) { lista.size }
    var ui by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<Media?>(null) }
    BackHandler { a.atras() }
    val actual = lista[pager.currentPage.coerceIn(0, lista.size - 1)]
    val externo = actual.id == -1L

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { lista[it].uri.toString() }, modifier = Modifier.fillMaxSize()) { page ->
            val m = lista[page]
            if (m.video) {
                PaginaVideo(m.uri, { a.abrirVideo(m) }) { ui = !ui }
            } else {
                FotoZoom(m.uri.toString(), { a.decodificar(m.uri, 2560) }) { ui = !ui }
            }
        }

        if (ui) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(if (externo) actual.name else fechaCompleta(actual.date), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!externo) Text("${pager.currentPage + 1} de ${lista.size}", fontSize = 12.sp, color = Suave)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Accion(Icons.Rounded.Share, "Compartir") { a.compartir(listOf(actual)) }
                if (!actual.video) Accion(Icons.Rounded.Edit, "Editar") { a.ir(Pant.Editor(actual)) }
                if (!externo) {
                    val fav = actual.uri.toString() in a.favoritos.value
                    Accion(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito") {
                        a.alternarFavorito(listOf(actual))
                    }
                    Accion(Icons.Rounded.Info, "Info") { info = actual }
                    if (a.bovedaAbierta.value) Accion(Icons.Rounded.Lock, "Privado") { a.ocultar(listOf(actual)) }
                    Accion(Icons.Rounded.Delete, "Papelera", Color(0xFFFF6B6B)) { a.aPapelera(listOf(actual)) }
                }
            }
        }
    }
    val i = info
    if (i != null) InfoDialog(a, i) { info = null }
}

@Composable
private fun PaginaVideo(uri: Uri, onPlay: () -> Unit, onToque: () -> Unit) {
    val context = LocalContext.current
    val portada by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, android.util.Size(1280, 1280), null)
            } catch (e: Exception) {
                null
            }
        }
    }
    PortadaVideo(portada, onPlay, onToque)
}

@Composable
private fun PortadaVideo(portada: Bitmap?, onPlay: () -> Unit, onToque: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onToque() }) }, contentAlignment = Alignment.Center) {
        if (portada != null) {
            Image(portada.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PlayCircle, "Reproducir", tint = Color.White, modifier = Modifier.size(80.dp)) }
    }
}

/** Foto con zoom: pellizca o toca dos veces. Sin zoom, deslizar cambia de foto. */
@Composable
private fun FotoZoom(clave: String, cargar: () -> Bitmap?, onToque: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, clave) {
        value = withContext(Dispatchers.IO) { cargar() }
    }
    var escala by remember(clave) { mutableFloatStateOf(1f) }
    var desp by remember(clave) { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(clave) {
                detectTapGestures(
                    onTap = { onToque() },
                    onDoubleTap = { p ->
                        if (escala > 1.05f) {
                            escala = 1f
                            desp = Offset.Zero
                        } else {
                            escala = 2.5f
                            val c = Offset(size.width / 2f, size.height / 2f)
                            desp = (c - p) * 1.5f
                        }
                    }
                )
            }
            .pointerInput(clave) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val dedos = ev.changes.count { it.pressed }
                        if (dedos >= 2 || escala > 1.05f) {
                            val z = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            escala = (escala * z).coerceIn(1f, 6f)
                            val mx = (escala - 1f) * size.width / 2f
                            val my = (escala - 1f) * size.height / 2f
                            desp = Offset(
                                (desp.x + pan.x).coerceIn(-mx, mx),
                                (desp.y + pan.y).coerceIn(-my, my)
                            )
                            if (escala <= 1.01f) desp = Offset.Zero
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b == null) {
            CircularProgressIndicator(color = Acento)
        } else {
            Image(
                b.asImageBitmap(), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = desp.x
                    translationY = desp.y
                }
            )
        }
    }
}

@Composable
private fun InfoDialog(a: MainActivity, m: Media, onCerrar: () -> Unit) {
    val exif by produceState<List<Pair<String, String>>>(emptyList(), m.uri) {
        value = withContext(Dispatchers.IO) {
            val out = mutableListOf<Pair<String, String>>()
            if (!m.video) try {
                a.contentResolver.openInputStream(m.uri)?.use { s ->
                    val e = ExifInterface(s)
                    val cam = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL))
                        .joinToString(" ").trim()
                    if (cam.isNotBlank()) out += "Cámara" to cam
                    val f = e.getAttribute(ExifInterface.TAG_F_NUMBER)
                    val t = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    val iso = e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    val partes = mutableListOf<String>()
                    if (f != null) partes += "f/$f"
                    if (t > 0) partes += if (t < 1) "1/${(1 / t).toInt()} s" else "${t} s"
                    if (iso != null) partes += "ISO $iso"
                    if (partes.isNotEmpty()) out += "Exposición" to partes.joinToString("  ·  ")
                    val ll = e.latLong
                    if (ll != null) out += "Ubicación" to String.format(Locale.US, "%.5f, %.5f", ll[0], ll[1])
                }
            } catch (ex: Exception) {
                // sin datos EXIF
            }
            out
        }
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = Acento) } },
        title = { Text(m.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Dato("Fecha", fechaCompleta(m.date))
                Dato("Ubicación del archivo", m.path + m.name)
                if (m.width > 0) Dato("Resolución", "${m.width} × ${m.height}  (${String.format(Locale.US, "%.1f", m.width * m.height / 1_000_000f)} MP)")
                Dato("Tamaño", formatSize(m.size))
                if (m.video) Dato("Duración", formatTime(m.duration))
                exif.forEach { (k, v) -> Dato(k, v) }
            }
        }
    )
}

@Composable
private fun Dato(k: String, v: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(k, fontSize = 13.sp, color = Acento)
        Text(v, fontSize = 15.sp)
    }
}

// ======================================================================
// Papelera
// ======================================================================

@Composable
private fun PapeleraScreen(a: MainActivity) {
    LaunchedEffect(Unit) { a.cargarPapelera() }
    val lista = a.papelera.value
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val elegidos = remember(lista, sel) { (lista ?: emptyList()).filter { it.id in sel } }
    BackHandler { if (sel.isNotEmpty()) sel = emptySet() else a.atras() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
            Text("Papelera", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (!lista.isNullOrEmpty()) {
                TextButton(onClick = { a.eliminarDefinitivo(lista) }) { Text("Vaciar", color = Color(0xFFFF6B6B), fontSize = 16.sp) }
            }
        }
        Text(
            "Toca para elegir. Lo que quede aquí se elimina solo a los 30 días.",
            color = Suave, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Box(Modifier.weight(1f)) {
            when {
                lista == null -> CircularProgressIndicator(color = Acento, modifier = Modifier.align(Alignment.Center))
                lista.isEmpty() -> Text("La papelera está vacía.", color = Suave, fontSize = 17.sp, modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                    items(lista.size, key = { lista[it].id }) { i ->
                        val m = lista[i]
                        val s = m.id in sel
                        Box(Modifier.clickable { sel = if (s) sel - m.id else sel + m.id }) {
                            CeldaMedia(m, s, true, false)
                            val dias = ((m.expires * 1000 - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                            Text(
                                "${dias}d", color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                                    .clip(RoundedCornerShape(5.dp)).background(Color(0x99000000)).padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        if (elegidos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(Tarjeta).padding(8.dp)) {
                Accion(Icons.Rounded.SelectAll, "Todo") { sel = (lista ?: emptyList()).map { it.id }.toSet() }
                Accion(Icons.Rounded.RestoreFromTrash, "Restaurar", Acento) { a.restaurar(elegidos); sel = emptySet() }
                Accion(Icons.Rounded.DeleteForever, "Eliminar", Color(0xFFFF6B6B)) { a.eliminarDefinitivo(elegidos); sel = emptySet() }
            }
        }
    }
}

// ======================================================================
// Ajustes
// ======================================================================

@Composable
private fun AjustesScreen(a: MainActivity) {
    BackHandler { a.atras() }
    var dialogoClave by remember { mutableStateOf(false) }
    var refrescar by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1500); refrescar++ } // por si regresas de Ajustes de Android
    }
    val gestiona = remember(refrescar) { a.gestionaMedios() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
            Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            TarjetaAjuste(
                "Borrar y mover sin confirmar",
                if (gestiona) "Activado: Baúl puede borrar y mover sin preguntarte cada vez."
                else "Android pide confirmar cada vez. Actívalo en «Administración de medios» para evitarlo.",
                if (gestiona) null else "Activar"
            ) { a.abrirPermisoGestion() }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste(
                "Clave privada",
                if (a.tieneClave()) "Configurada." else "Sin configurar.",
                if (a.tieneClave()) "Cambiar" else "Crear"
            ) { dialogoClave = true }
            Spacer(Modifier.height(12.dp))
            TarjetaAjuste("Tamaño de miniaturas", "Pellizca la cuadrícula de fotos para hacerlas más grandes o pequeñas.", null) {}
            Spacer(Modifier.height(24.dp))
            Text("Baúl 1.0 · Tecnología Universal Avanzada", color = Suave, fontSize = 13.sp)
        }
    }
    if (dialogoClave) ClaveDialog(a) { dialogoClave = false }
}

@Composable
private fun TarjetaAjuste(titulo: String, texto: String, boton: String?, onBoton: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tarjeta).padding(16.dp)) {
        Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(texto, fontSize = 14.sp, color = Suave, modifier = Modifier.padding(top = 4.dp))
        if (boton != null) {
            Button(
                onClick = onBoton,
                colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Color.Black),
                modifier = Modifier.padding(top = 10.dp)
            ) { Text(boton) }
        }
    }
}

@Composable
private fun CampoClave(valor: String, etiqueta: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { onCambio(it.take(32)) },
        singleLine = true,
        label = { Text(etiqueta) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun ClaveDialog(a: MainActivity, onCerrar: () -> Unit) {
    val tiene = a.tieneClave()
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (tiene) "Cambiar clave" else "Crear clave") },
        text = {
            Column {
                if (tiene) CampoClave(actual, "Clave actual") { actual = it }
                CampoClave(nueva, "Clave nueva (mínimo 4)") { nueva = it }
                CampoClave(repetir, "Repetir clave") { repetir = it }
                Text(
                    "Usa algo que no escribirías al buscar fotos. Si la olvidas no hay forma de recuperarla.",
                    fontSize = 13.sp, color = Suave, modifier = Modifier.padding(top = 6.dp)
                )
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    tiene && !a.claveCorrecta(actual) -> "La clave actual no es correcta"
                    nueva.length < 4 -> "Debe tener al menos 4 caracteres"
                    nueva != repetir -> "Las claves no coinciden"
                    else -> ""
                }
                if (error.isBlank()) {
                    a.ponerClave(nueva)
                    onCerrar()
                }
            }) { Text("Guardar", color = Acento) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = Suave) } }
    )
}

// ======================================================================
// Área privada (bóveda)
// ======================================================================

private val miniBoveda = LruCache<String, Bitmap>(200)

@Composable
private fun MiniaturaBoveda(a: MainActivity, item: VItem, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(miniBoveda.get(item.file), item.file) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { Boveda.miniatura(a, item)?.also { miniBoveda.put(item.file, it) } }
        }
    }
    Box(modifier.background(Tarjeta)) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

private enum class DialogoBoveda { NADA, RESPALDAR, IMPORTAR }

@Composable
private fun BovedaScreen(a: MainActivity) {
    val items = a.bovedaItems.value
    var sel by remember { mutableStateOf(setOf<String>()) }
    var dialogo by remember { mutableStateOf(DialogoBoveda.NADA) }
    val elegidos = remember(items, sel) { items.filter { it.file in sel } }
    BackHandler { if (sel.isNotEmpty()) sel = emptySet() else a.atras() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("Privado", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${items.size} elementos", fontSize = 13.sp, color = Suave)
            }
            IconoBoton(Icons.Rounded.Backup, "Respaldar") { dialogo = DialogoBoveda.RESPALDAR }
            IconoBoton(Icons.Rounded.UploadFile, "Importar respaldo") { dialogo = DialogoBoveda.IMPORTAR }
            IconoBoton(Icons.Rounded.Lock, "Cerrar") { a.cerrarBoveda() }
        }
        Box(Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Text(
                    "Vacío.\n\nPara guardar aquí: en la galería selecciona fotos o videos y toca «Privado». " +
                        "Esa opción solo aparece mientras esta área está abierta.",
                    color = Suave, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                    items(items.size, key = { items[it].file }) { i ->
                        val v = items[i]
                        val s = v.file in sel
                        Box(
                            Modifier.aspectRatio(1f).padding(1.dp).combinedClickable(
                                onClick = {
                                    if (sel.isNotEmpty()) sel = if (s) sel - v.file else sel + v.file
                                    else a.ir(Pant.VisorBoveda(i))
                                },
                                onLongClick = { sel = if (s) sel - v.file else sel + v.file }
                            )
                        ) {
                            MiniaturaBoveda(a, v, Modifier.fillMaxSize())
                            if (v.video) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp))
                            }
                            if (s) Box(Modifier.fillMaxSize().background(Acento.copy(alpha = 0.28f)).border(3.dp, Acento))
                            if (sel.isNotEmpty()) {
                                Icon(
                                    if (s) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                    tint = if (s) Acento else Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (a.guardando.intValue > 0) CircularProgressIndicator(color = Acento, modifier = Modifier.align(Alignment.Center))
        }
        if (elegidos.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(Tarjeta).padding(8.dp)) {
                Accion(Icons.Rounded.SelectAll, "Todo") { sel = items.map { it.file }.toSet() }
                Accion(Icons.Rounded.Unarchive, "Sacar", Acento) { a.sacarDeBoveda(elegidos); sel = emptySet() }
                Accion(Icons.Rounded.DeleteForever, "Eliminar", Color(0xFFFF6B6B)) { a.eliminarDeBoveda(elegidos); sel = emptySet() }
            }
        }
    }

    if (dialogo != DialogoBoveda.NADA) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        val respaldar = dialogo == DialogoBoveda.RESPALDAR
        AlertDialog(
            onDismissRequest = { dialogo = DialogoBoveda.NADA },
            title = { Text(if (respaldar) "Respaldar" else "Importar respaldo") },
            text = {
                Column {
                    Text(
                        if (respaldar) "Se crea un archivo protegido con tu clave. Guárdalo en tu computadora: " +
                            "si desinstalas Baúl o borras sus datos, este espacio se pierde."
                        else "Elige un archivo .baul y escribe la clave con la que se hizo ese respaldo.",
                        fontSize = 14.sp, color = Suave
                    )
                    CampoClave(pin, "Clave") { pin = it }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (respaldar) {
                        if (!a.claveCorrecta(pin)) {
                            error = "Clave incorrecta"
                        } else {
                            a.pedirRespaldo(pin)
                            dialogo = DialogoBoveda.NADA
                        }
                    } else {
                        if (pin.length < 4) error = "Escribe la clave del respaldo"
                        else {
                            a.pedirImportar(pin)
                            dialogo = DialogoBoveda.NADA
                        }
                    }
                }) { Text("Continuar", color = Acento) }
            },
            dismissButton = { TextButton(onClick = { dialogo = DialogoBoveda.NADA }) { Text("Cancelar", color = Suave) } }
        )
    }
}

@Composable
private fun VisorBovedaScreen(a: MainActivity, inicio: Int) {
    val items = a.bovedaItems.value
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { a.atras() }
        return
    }
    val pager = rememberPagerState(initialPage = inicio.coerceIn(0, items.size - 1)) { items.size }
    var ui by remember { mutableStateOf(true) }
    BackHandler { a.atras() }
    val actual = items[pager.currentPage.coerceIn(0, items.size - 1)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { items[it].file }, modifier = Modifier.fillMaxSize()) { page ->
            val v = items[page]
            if (v.video) {
                val portada by produceState<Bitmap?>(null, v.file) {
                    value = withContext(Dispatchers.IO) { Boveda.miniatura(a, v) }
                }
                PortadaVideo(portada, { a.abrirVideoBoveda(v) }) { ui = !ui }
            } else {
                FotoZoom(v.file, { a.decodificarArchivo(Boveda.archivo(a, v), 2560) }) { ui = !ui }
            }
        }
        if (ui) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).windowInsetsPadding(WindowInsets.safeDrawing).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconoBoton(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar") { a.atras() }
                Text(fechaCompleta(actual.date), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp))
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)
            ) {
                Accion(Icons.Rounded.Unarchive, "Sacar", Acento) { a.sacarDeBoveda(listOf(actual)) }
                Accion(Icons.Rounded.DeleteForever, "Eliminar", Color(0xFFFF6B6B)) { a.eliminarDeBoveda(listOf(actual)) }
            }
        }
    }
}

@Suppress("unused")
private fun noUsado(w: Int) = Modifier.width(w.dp)
