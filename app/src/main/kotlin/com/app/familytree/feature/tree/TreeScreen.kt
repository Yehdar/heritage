package com.app.familytree.feature.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.app.familytree.core.model.*
import com.app.familytree.core.theme.*
import com.app.familytree.core.ui.*
import kotlin.math.roundToInt

data class GraphViewport(val zoom: Float = 1f, val pan: Offset = Offset.Zero)

@Composable fun TreeScreen(state: TreeViewState, viewport: GraphViewport, onTransform: (Offset, Float) -> Unit, onFit: () -> Unit, onSelect: (String) -> Unit, onMenu: () -> Unit, onDismiss: () -> Unit, onAdd: (RelationshipType) -> Unit, onEdit: (String, String, String) -> Unit, onSave: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeading("Our living tree", "THE DOE FAMILY", state.subtitle)
        ErrorMessage(state.error, onRetry)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("treeGraph")) {
            val density = LocalDensity.current
            val transform by rememberUpdatedState(onTransform)
            val width = maxWidth.value; val height = maxHeight.value
            val graphWidth = (state.graph.nodes.maxOfOrNull { it.positionX } ?: 65f) + 65f
            val graphHeight = (state.graph.nodes.maxOfOrNull { it.positionY } ?: 55f) + 100f
            val fit = minOf((width - 24f) / graphWidth, (height - 64f) / graphHeight).coerceAtLeast(0.08f)
            val scale = fit * viewport.zoom
            val shift = Offset((width - graphWidth * scale) / 2f, (height - graphHeight * scale) / 2f) + viewport.pan
            fun position(node: TreeNode) = Offset(node.positionX * scale + shift.x, node.positionY * scale + shift.y)
            val nodes = state.graph.nodes.associateBy { it.person.id }
            val radius = 27f * scale
            Canvas(Modifier.fillMaxSize().pointerInput(density.density) { detectTransformGestures { _, pan, zoom, _ -> transform(pan / density.density, zoom) } }) {
                fun px(value: Offset) = value * density.density
                state.graph.descentLines.forEach { (parent, child) ->
                    val a = nodes[parent]?.let(::position) ?: return@forEach
                    val b = nodes[child]?.let(::position) ?: return@forEach
                    val start = px(a + Offset(0f, radius)); val end = px(b - Offset(0f, radius))
                    val middle = (start.y + end.y) / 2f
                    val path = Path().apply { moveTo(start.x, start.y); lineTo(start.x, middle); lineTo(end.x, middle); lineTo(end.x, end.y) }
                    drawPath(path, SoftBrown, style = Stroke(1.2.dp.toPx()))
                }
                state.graph.marriages.forEach { marriage ->
                    val a = nodes[marriage.person1Id]?.let(::position) ?: return@forEach
                    val b = nodes[marriage.person2Id]?.let(::position) ?: return@forEach
                    val direction = if (b.x > a.x) 1f else -1f
                    val start = px(a + Offset(radius * direction, 0f)); val end = px(b - Offset(radius * direction, 0f))
                    val path = Path().apply { moveTo(start.x, start.y); cubicTo(start.x + (end.x-start.x)/3f, start.y - 8.dp.toPx(), end.x - (end.x-start.x)/3f, end.y - 8.dp.toPx(), end.x, end.y) }
                    drawPath(path, SoftBrown, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                }
            }
            state.graph.marriages.forEach { marriage ->
                val a = nodes[marriage.person1Id]?.let(::position); val b = nodes[marriage.person2Id]?.let(::position)
                if (a != null && b != null) Surface(Modifier.offset { IntOffset((((a.x+b.x)/2f - 30f) * density.density).roundToInt(), (((a.y+b.y)/2f - 22f) * density.density).roundToInt()) }, shape = CircleShape, color = Beige) { Text(marriage.yearLabel, Modifier.padding(3.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)) }
            }
            state.graph.nodes.forEach { node ->
                val point = position(node)
                val avatarSize = (54f * scale).coerceAtLeast(24f)
                val selected = node.person.id == state.selectedNodeId
                Column(Modifier.offset { IntOffset(((point.x - 48f) * density.density).roundToInt(), ((point.y - avatarSize/2f) * density.density).roundToInt()) }.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Box(Modifier.size(avatarSize.dp.coerceAtLeast(48.dp)).clickable { onSelect(node.person.id) }.semantics { contentDescription = "Select ${node.person.name}" }, contentAlignment = Alignment.Center) {
                            Avatar(node.person, Modifier.size(avatarSize.dp).then(if (selected) Modifier.border(2.dp, Espresso, CircleShape) else Modifier))
                        }
                        if (selected) {
                            Box(Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-14).dp)) {
                                Box(Modifier.size(48.dp).testTag("addBadge").clickable(onClick = onMenu).semantics { contentDescription = "Add relative" }, contentAlignment = Alignment.Center) {
                                    Surface(Modifier.size(28.dp), shape = CircleShape, color = Color.White, shadowElevation = 2.dp) { Icon(Icons.Outlined.Add, null, Modifier.padding(4.dp), tint = Espresso) }
                                }
                                DropdownMenu(expanded = state.isAddMenuExpanded, onDismissRequest = onDismiss, offset = DpOffset(32.dp, (-48).dp), containerColor = Color.White) {
                                    RelationshipType.entries.forEach { type -> DropdownMenuItem(text = { Text("Add ${type.name.lowercase().replaceFirstChar { it.uppercase() }}") }, onClick = { onAdd(type) }) }
                                }
                            }
                        }
                    }
                    Text(node.person.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            FilledTonalButton(onClick = onFit, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(6.dp)); Text("Fit tree") }
        }
    }
    state.form?.let { RelationDialog(it, onEdit, onSave, onDismiss) }
}
