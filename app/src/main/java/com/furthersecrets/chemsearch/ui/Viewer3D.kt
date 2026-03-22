package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
data class Atom3D(val x: Float, val y: Float, val z: Float, val element: String)
data class Bond3D(val a1: Int, val a2: Int, val type: Int)
data class Molecule3D(val atoms: List<Atom3D>, val bonds: List<Bond3D>)

// SDF PARSER
fun parseSdf(sdf: String): Molecule3D {
    val lines = sdf.lines()
    if (lines.size < 4) return Molecule3D(emptyList(), emptyList())
    val countsLine = lines[3]
    val atomCount = countsLine.substring(0, 3).trim().toIntOrNull()
        ?: return Molecule3D(emptyList(), emptyList())
    val bondCount = countsLine.substring(3, 6).trim().toIntOrNull() ?: 0
    val atoms = mutableListOf<Atom3D>()
    val bonds = mutableListOf<Bond3D>()

    for (i in 0 until atomCount) {
        val line = lines.getOrNull(4 + i) ?: break
        if (line.length < 34) continue
        val x = line.substring(0, 10).trim().toFloatOrNull() ?: 0f
        val y = line.substring(10, 20).trim().toFloatOrNull() ?: 0f
        val z = line.substring(20, 30).trim().toFloatOrNull() ?: 0f
        val element = line.substring(31, minOf(34, line.length)).trim()
            .replace(Regex("[^A-Za-z]"), "")
            .replaceFirstChar { it.uppercase() }
        atoms.add(Atom3D(x, y, z, element))
    }

    for (i in 0 until bondCount) {
        val line = lines.getOrNull(4 + atomCount + i) ?: break
        if (line.length < 9) continue
        val a1 = (line.substring(0, 3).trim().toIntOrNull() ?: 1) - 1
        val a2 = (line.substring(3, 6).trim().toIntOrNull() ?: 1) - 1
        val type = line.substring(6, 9).trim().toIntOrNull() ?: 1
        if (a1 in atoms.indices && a2 in atoms.indices) bonds.add(Bond3D(a1, a2, type))
    }

    return Molecule3D(atoms, bonds)
}

// CPK COLORS & BALL-AND-STICK RADII
private val cpkColors = mapOf(
    // Full standard Jmol CPK colors for ALL 118 elements
    "H"  to Color(0xFFFFFFFF), "He" to Color(0xFFD9FFFF),
    "Li" to Color(0xFFCC80FF), "Be" to Color(0xFFC2FF00),
    "B"  to Color(0xFFFFB5B5), "C"  to Color(0xFF2C2C2C),
    "N"  to Color(0xFF3050F8), "O"  to Color(0xFFFF0D0D),
    "F"  to Color(0xFF90E050), "Ne" to Color(0xFFB3E3F5),
    "Na" to Color(0xFFAB5CF2), "Mg" to Color(0xFF8AFF00),
    "Al" to Color(0xFFBFA6A6), "Si" to Color(0xFFF0C8A0),
    "P"  to Color(0xFFFF8000), "S"  to Color(0xFFFFFF30),
    "Cl" to Color(0xFF1FF01F), "Ar" to Color(0xFF80D1E3),
    "K"  to Color(0xFF8F40D4), "Ca" to Color(0xFF3DFF00),
    "Sc" to Color(0xFFE6E6E6), "Ti" to Color(0xFFBFC2C7),
    "V"  to Color(0xFFA6A6AB), "Cr" to Color(0xFF8A99C7),
    "Mn" to Color(0xFF9C7AC7), "Fe" to Color(0xFFE06633),
    "Co" to Color(0xFFF090A0), "Ni" to Color(0xFF50D050),
    "Cu" to Color(0xFFC88033), "Zn" to Color(0xFF7D80B0),
    "Ga" to Color(0xFFC28F8F), "Ge" to Color(0xFF668F8F),
    "As" to Color(0xFFBD80E3), "Se" to Color(0xFFFFA100),
    "Br" to Color(0xFFA62929), "Kr" to Color(0xFF5CB8D1),
    "Rb" to Color(0xFF702EB0), "Sr" to Color(0xFF00FF00),
    "Y"  to Color(0xFF94FFFF), "Zr" to Color(0xFF94E0E0),
    "Nb" to Color(0xFF73C2C9), "Mo" to Color(0xFF54B5B5),
    "Tc" to Color(0xFF3B9E9E), "Ru" to Color(0xFF248F8F),
    "Rh" to Color(0xFF0A7D8C), "Pd" to Color(0xFF006985),
    "Ag" to Color(0xFFC0C0C0), "Cd" to Color(0xFFFFD98F),
    "In" to Color(0xFFA67573), "Sn" to Color(0xFF668080),
    "Sb" to Color(0xFF9E63B5), "Te" to Color(0xFFD47A00),
    "I"  to Color(0xFF940094), "Xe" to Color(0xFF429EB0),
    "Cs" to Color(0xFF57178F), "Ba" to Color(0xFF00C900),
    "La" to Color(0xFF70D4FF), "Ce" to Color(0xFFFFFFC7),
    "Pr" to Color(0xFFD9FFC7), "Nd" to Color(0xFFC7FFC7),
    "Pm" to Color(0xFFA3FFC7), "Sm" to Color(0xFF8FFFC7),
    "Eu" to Color(0xFF61FFC7), "Gd" to Color(0xFF45FFC7),
    "Tb" to Color(0xFF30FFC7), "Dy" to Color(0xFF1FFFC7),
    "Ho" to Color(0xFF00FF9C), "Er" to Color(0xFF00E675),
    "Tm" to Color(0xFF00D452), "Yb" to Color(0xFF00BF38),
    "Lu" to Color(0xFF00AB24), "Hf" to Color(0xFF4DC2FF),
    "Ta" to Color(0xFF4DA6FF), "W"  to Color(0xFF2194D6),
    "Re" to Color(0xFF267DAB), "Os" to Color(0xFF266696),
    "Ir" to Color(0xFF175487), "Pt" to Color(0xFFD0D0E0),
    "Au" to Color(0xFFFFD123), "Hg" to Color(0xFFB8B8D0),
    "Tl" to Color(0xFFA6544D), "Pb" to Color(0xFF575961),
    "Bi" to Color(0xFF9E4FB5), "Po" to Color(0xFFAB5C00),
    "At" to Color(0xFF754F45), "Rn" to Color(0xFF428296),
    "Fr" to Color(0xFF420066), "Ra" to Color(0xFF007D00),
    "Ac" to Color(0xFF70ABFA), "Th" to Color(0xFF00BAFF),
    "Pa" to Color(0xFF00A1FF), "U"  to Color(0xFF008FFF),
    "Np" to Color(0xFF0080FF), "Pu" to Color(0xFF006BFF),
    "Am" to Color(0xFF545CF2), "Cm" to Color(0xFF785CE3),
    "Bk" to Color(0xFF8A4FE3), "Cf" to Color(0xFFA136D4),
    "Es" to Color(0xFFB31FD4), "Fm" to Color(0xFFB31FBA),
    "Md" to Color(0xFFB30DA6), "No" to Color(0xFFBD0D87),
    "Lr" to Color(0xFFC70066), "Rf" to Color(0xFFCC0059),
    "Db" to Color(0xFFD1004F), "Sg" to Color(0xFFD90045),
    "Bh" to Color(0xFFE00038), "Hs" to Color(0xFFE6002E),
    "Mt" to Color(0xFFEB0026),
    // Superheavy elements (Natural Gray)
    "Ds" to Color(0xFF808080), "Rg" to Color(0xFF808080),
    "Cn" to Color(0xFF808080), "Nh" to Color(0xFF808080),
    "Fl" to Color(0xFF808080), "Mc" to Color(0xFF808080),
    "Lv" to Color(0xFF808080), "Ts" to Color(0xFF808080),
    "Og" to Color(0xFF808080)
)

private val ballRadii = mapOf(
    "H"  to 0.22f, "C"  to 0.40f, "N"  to 0.37f, "O"  to 0.35f,
    "F"  to 0.30f, "Cl" to 0.46f, "Br" to 0.52f, "I"  to 0.58f,
    "S"  to 0.44f, "P"  to 0.42f, "Na" to 0.50f, "K"  to 0.56f,
    "Ca" to 0.50f, "Fe" to 0.42f, "Cu" to 0.42f, "Zn" to 0.42f,
    "He" to 0.28f, "Li" to 0.52f, "Be" to 0.45f, "B"  to 0.39f,
    "Ne" to 0.30f, "Mg" to 0.44f, "Al" to 0.50f, "Si" to 0.46f,
    "Ar" to 0.47f, "Sc" to 0.47f, "Ti" to 0.47f,
    "V"  to 0.42f, "Cr" to 0.42f, "Mn" to 0.42f,
    "Co" to 0.42f, "Ni" to 0.42f, "Ga" to 0.50f,
    "Ge" to 0.50f, "As" to 0.46f, "Se" to 0.46f,
    "Kr" to 0.50f, "Rb" to 0.60f, "Sr" to 0.55f,
    "Y"  to 0.47f, "Zr" to 0.47f, "Nb" to 0.42f,
    "Mo" to 0.42f, "Tc" to 0.42f, "Ru" to 0.42f,
    "Rh" to 0.42f, "Pd" to 0.42f, "Ag" to 0.47f,
    "Cd" to 0.47f, "In" to 0.52f, "Sn" to 0.52f,
    "Sb" to 0.47f, "Te" to 0.47f, "Xe" to 0.54f,
    "Cs" to 0.62f, "Ba" to 0.55f, "La" to 0.47f,
    "Ce" to 0.47f, "Pr" to 0.47f, "Nd" to 0.47f,
    "Pm" to 0.47f, "Sm" to 0.47f, "Eu" to 0.47f,
    "Gd" to 0.47f, "Tb" to 0.47f, "Dy" to 0.47f,
    "Ho" to 0.47f, "Er" to 0.47f, "Tm" to 0.47f,
    "Yb" to 0.47f, "Lu" to 0.47f, "Hf" to 0.47f,
    "Ta" to 0.47f, "W"  to 0.42f, "Re" to 0.42f,
    "Os" to 0.42f, "Ir" to 0.42f, "Pt" to 0.42f,
    "Au" to 0.47f, "Hg" to 0.45f, "Tl" to 0.52f,
    "Pb" to 0.54f, "Bi" to 0.52f, "Po" to 0.50f,
    "At" to 0.52f, "Rn" to 0.55f, "Fr" to 0.65f,
    "Ra" to 0.55f, "Ac" to 0.47f, "Th" to 0.47f,
    "Pa" to 0.47f, "U"  to 0.47f, "Np" to 0.47f,
    "Pu" to 0.47f, "Am" to 0.47f, "Cm" to 0.47f,
    "Bk" to 0.47f, "Cf" to 0.47f, "Es" to 0.47f,
    "Fm" to 0.47f, "Md" to 0.47f, "No" to 0.47f,
    "Lr" to 0.47f, "Rf" to 0.45f, "Db" to 0.45f,
    "Sg" to 0.45f, "Bh" to 0.45f, "Hs" to 0.45f,
    "Mt" to 0.45f, "Ds" to 0.45f, "Rg" to 0.45f,
    "Cn" to 0.45f, "Nh" to 0.45f, "Fl" to 0.45f,
    "Mc" to 0.45f, "Lv" to 0.45f, "Ts" to 0.45f,
    "Og" to 0.45f
)

fun elementColor(element: String): Color = cpkColors[element] ?: Color(0xFFFF69B4)
fun elementBallRadius(element: String): Float = ballRadii[element] ?: 0.35f

// 3D Math
private fun rotate3D(
    x: Float, y: Float, z: Float, rx: Float, ry: Float
): Triple<Float, Float, Float> {
    val x1 = x * cos(ry) + z * sin(ry)
    val z1 = -x * sin(ry) + z * cos(ry)
    val y2 = y * cos(rx) - z1 * sin(rx)
    val z2 = y * sin(rx) + z1 * cos(rx)
    return Triple(x1, y2, z2)
}

private fun project(
    x: Float, y: Float, z: Float,
    cx: Float, cy: Float,
    scale: Float, zoom: Float,
    fov: Float = 10f
): Pair<Offset, Float> {
    val p = fov / (fov + z)
    val sx = cx + x * scale * zoom * p
    val sy = cy - y * scale * zoom * p
    return Pair(Offset(sx, sy), p)
}

// BOND DRAWING
private fun DrawScope.drawBond(
    s1: Offset, r1: Float,
    s2: Offset, r2: Float,
    bondType: Int,
    bondStroke: Float,
    color: Color
) {
    val dx = s2.x - s1.x
    val dy = s2.y - s1.y
    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val ux = dx / dist
    val uy = dy / dist

    val startX = s1.x + ux * (r1 * 0.85f)
    val startY = s1.y + uy * (r1 * 0.85f)
    val endX   = s2.x - ux * (r2 * 0.85f)
    val endY   = s2.y - uy * (r2 * 0.85f)

    if (dist < (r1 + r2) * 0.5f) return

    val px = -uy * bondStroke * 2.2f
    val py =  ux * bondStroke * 2.2f

    val start = Offset(startX, startY)
    val end   = Offset(endX, endY)

    when (bondType) {
        2 -> {
            drawLine(color, Offset(startX + px, startY + py), Offset(endX + px, endY + py), strokeWidth = bondStroke)
            drawLine(color, Offset(startX - px, startY - py), Offset(endX - px, endY - py), strokeWidth = bondStroke)
        }
        3 -> {
            drawLine(color, start, end, strokeWidth = bondStroke)
            drawLine(color,
                Offset(startX + px * 1.8f, startY + py * 1.8f),
                Offset(endX   + px * 1.8f, endY   + py * 1.8f),
                strokeWidth = bondStroke * 0.85f)
            drawLine(color,
                Offset(startX - px * 1.8f, startY - py * 1.8f),
                Offset(endX   - px * 1.8f, endY   - py * 1.8f),
                strokeWidth = bondStroke * 0.85f)
        }
        else -> drawLine(color, start, end, strokeWidth = bondStroke)
    }
}

// ATOM DRAWING
private fun DrawScope.drawAtom(pos: Offset, r: Float, color: Color, depthFactor: Float, rotX: Float, rotY: Float) {
    val lx = cos(rotY) * (-0.6f)
    val ly = (-0.6f) * cos(rotX)
    val hlX = pos.x + lx * r * 0.55f
    val hlY = pos.y + ly * r * 0.55f

    drawCircle(Color.Black.copy(0.28f), r * 1.06f, Offset(pos.x + r * 0.08f, pos.y + r * 0.08f))
    drawCircle(color, r, pos)
    drawCircle(Color.Black.copy(0.20f), r, pos, style = Stroke(r * 0.18f))
    drawCircle(Color.White.copy(0.55f), r * 0.36f, Offset(hlX, hlY))
    drawCircle(Color.White.copy(0.12f), r * 0.65f, Offset(pos.x + lx * r * 0.25f, pos.y + ly * r * 0.25f))
}

// MAIN COMPOSABLE
@Composable
fun Viewer3D(cid: Long, sdfData: String, isDark: Boolean = true) {
    val molecule = remember(cid, sdfData) { parseSdf(sdfData) }

    var rotX by remember { mutableFloatStateOf(0.25f) }
    var rotY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var isInteracting by remember { mutableStateOf(false) }
    var autoSpin by remember { mutableStateOf(true) }

    LaunchedEffect(cid) {
        rotX = 0.25f; rotY = 0f; zoom = 1f; autoSpin = true; isInteracting = false
    }

    LaunchedEffect(cid) {
        while (isActive) {
            delay(16L)
            if (autoSpin && !isInteracting) {
                rotY += 0.006f
                if (rotY > 2 * PI.toFloat()) rotY -= 2 * PI.toFloat()
            }
        }
    }

    if (molecule.atoms.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not parse 3D data", color = Color.White.copy(0.4f), style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val avgX = molecule.atoms.map { it.x }.average().toFloat()
    val avgY = molecule.atoms.map { it.y }.average().toFloat()
    val avgZ = molecule.atoms.map { it.z }.average().toFloat()
    val centered = molecule.atoms.map { it.copy(x = it.x - avgX, y = it.y - avgY, z = it.z - avgZ) }
    val maxExtent = centered.maxOf { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }.coerceIn(1.5f, Float.MAX_VALUE)

    val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val overlayTextColor = if (isDark) Color.White else Color.Black
    Box(Modifier.fillMaxSize().background(bgColor)) {

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cid) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        isInteracting = true
                        autoSpin = false
                        zoom = (zoom * gestureZoom).coerceIn(0.25f, 6f)
                        rotY += pan.x * 0.007f
                        rotX -= pan.y * 0.007f
                        rotX = rotX.coerceIn(-PI.toFloat() / 2, PI.toFloat() / 2)
                    }
                }
                .pointerInput(cid) {
                    while (true) {
                        awaitPointerEventScope {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.changes.all { !it.pressed }) {
                                isInteracting = false
                            }
                        }
                    }
                }
        ) {
            val screenCx = size.width / 2f
            val screenCy = size.height / 2f
            val viewSize = size.width.coerceAtMost(size.height)
            val baseScale = (viewSize * 0.28f) / maxExtent
            val bondStroke = (baseScale * zoom * 0.10f).coerceIn(2.5f, 9f)

            val rawProjected = centered.mapIndexed { _, atom ->
                val (rx, ry, rz) = rotate3D(atom.x, atom.y, atom.z, rotX, rotY)
                val (pos, p) = project(rx, ry, rz, screenCx, screenCy, baseScale, zoom)
                Proj(pos, rz, p.coerceIn(0.75f, 1.3f), atom.element, 0f)
            }

            val minDepth = rawProjected.minOf { it.depth }
            val maxDepth = rawProjected.maxOf { it.depth }
            val depthRange = (maxDepth - minDepth).coerceAtLeast(0.001f)

            val projected = rawProjected.map { atom ->
                atom.copy(depthFactor = ((atom.depth - minDepth) / depthRange).coerceIn(0f, 1f))
            }

            fun screenR(el: String, dp: Float) =
                (elementBallRadius(el) * baseScale * zoom * dp).coerceIn(4f, 60f)

            val drawCalls = mutableListOf<DrawCall>()

            projected.forEach { drawCalls.add(DrawCall.AtomCall(it)) }

            molecule.bonds.forEach { bond ->
                val a1 = projected.getOrNull(bond.a1) ?: return@forEach
                val a2 = projected.getOrNull(bond.a2) ?: return@forEach
                val avgDepth = (a1.depth + a2.depth) / 2f - 0.05f
                drawCalls.add(DrawCall.BondCall(a1, a2, bond.type, avgDepth))
            }
            drawCalls.sortByDescending {
                when (it) {
                    is DrawCall.AtomCall -> it.proj.depth
                    is DrawCall.BondCall -> it.depth
                }
            }
            val bondColor = Color(0xFF8899BB)
            drawCalls.forEach { call ->
                when (call) {
                    is DrawCall.AtomCall -> drawAtom(
                        call.proj.pos,
                        screenR(call.proj.element, call.proj.depthP),
                        elementColor(call.proj.element),
                        call.proj.depthFactor,
                        rotX,
                        rotY
                    )
                    is DrawCall.BondCall -> drawBond(
                        call.a1.pos, screenR(call.a1.element, call.a1.depthP),
                        call.a2.pos, screenR(call.a2.element, call.a2.depthP),
                        call.bondType, bondStroke, bondColor
                    )
                }
            }
        }

// Top left: reset button
        IconButton(
            onClick = { rotX = 0.25f; rotY = 0f; zoom = 1f; autoSpin = true },
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(32.dp)
        ) {
            Icon(Icons.Default.Refresh, "Reset view", tint = overlayTextColor.copy(0.4f), modifier = Modifier.size(16.dp))
        }

        // Top right: hints
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Drag to rotate", color = overlayTextColor.copy(0.35f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("Pinch to zoom",  color = overlayTextColor.copy(0.35f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        // Bottom left: element legend
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            molecule.atoms.map { it.element }.distinct().take(7).forEach { el ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(9.dp).background(elementColor(el), RoundedCornerShape(50)))
                    Text(el, color = overlayTextColor.copy(0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Bottom right: stats and spin indicator
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "${molecule.atoms.size} atoms  •  ${molecule.bonds.size} bonds",
                color = overlayTextColor.copy(0.35f), fontSize = 9.sp, fontFamily = FontFamily.Monospace
            )
            Text(
                if (autoSpin) "⟳ Auto-spin" else "● Paused",
                color = if (autoSpin) Color(0xFF3B82F6).copy(0.7f) else overlayTextColor.copy(0.3f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}
private data class Proj(
    val pos: Offset,
    val depth: Float,
    val depthP: Float,
    val element: String,
    val depthFactor: Float
)

private sealed class DrawCall {
    data class AtomCall(val proj: Proj) : DrawCall()
    data class BondCall(
        val a1: Proj, val a2: Proj,
        val bondType: Int, val depth: Float
    ) : DrawCall()
}