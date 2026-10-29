package com.naqaa.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.naqaa.app.R
import com.naqaa.app.content.Content
import com.naqaa.app.content.PlanDay
import com.naqaa.app.data.EventKind
import com.naqaa.app.data.Session
import com.naqaa.app.ui.AppViewModel
import com.naqaa.app.ui.UiState
import com.naqaa.app.ui.theme.Neon
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** What a point of the map is showing. */
private enum class NodeState { DONE, TODAY, MISSED, LOCKED }

/**
 * The thirty day plan drawn as a game map: one glowing point per day along a winding path
 * that climbs towards the horizon, with a small figure standing on today's point. Pressing a
 * point opens a bubble with that day's three tasks, each with its own check box.
 *
 * The depth is a projection trick, not a 3D engine: points and the path shrink towards the
 * horizon, a perspective floor grid converges on a vanishing point, and the figure is built
 * from shaded shapes with a cast shadow and a walking animation. Everything is drawn on one
 * canvas, so it needs no extra library and costs little while idle.
 */
@Composable
fun PlanScreen(viewModel: AppViewModel, state: UiState) {
    val language = state.preferences.language
    val plan = remember { Content.plan() }
    val start = state.preferences.planStart

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ScreenTitle(title = stringResource(R.string.plan_title), subtitle = stringResource(R.string.plan_subtitle))

        if (start == null || plan.isEmpty()) {
            SectionCard(title = stringResource(R.string.plan_not_started)) {
                Text(text = stringResource(R.string.plan_intro), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = viewModel::startPlanToday, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.plan_start))
                }
            }
            Spacer(Modifier.height(20.dp))
            return
        }

        val zone = ZoneId.systemDefault()
        val legacyDone = remember(state.events) {
            state.events.filter { it.kind == EventKind.PLAN }
                .map { LocalDate.ofInstant(it.at, zone) }
                .toSet()
        }
        val tasks = state.preferences.planTasks
        val currentDay = state.planDay ?: plan.size
        val developer = Session.developer

        fun isDone(day: Int): Boolean =
            AppViewModel.PLAN_TASKS.all { "$day:$it" in tasks } || legacyDone.contains(start.plusDays((day - 1).toLong()))

        val doneCount = plan.count { isDone(it.day) }

        SectionCard(title = stringResource(R.string.plan_progress)) {
            StatLine(stringResource(R.string.plan_current_day), currentDay.toString())
            StatLine(stringResource(R.string.plan_done_days), doneCount.toString())
        }

        var selected by remember { mutableStateOf<Int?>(null) }

        PlanMap(
            plan = plan,
            currentDay = currentDay,
            stateOf = { day ->
                when {
                    isDone(day) -> NodeState.DONE
                    day == currentDay -> NodeState.TODAY
                    day < currentDay -> NodeState.MISSED
                    else -> NodeState.LOCKED
                }
            },
            selected = selected,
            onSelect = { selected = it },
            bubble = { entry ->
                DayBubble(
                    entry = entry,
                    language = language,
                    unlocked = entry.day <= currentDay || developer,
                    taskDone = { task -> "${entry.day}:$task" in tasks },
                    onToggle = { task -> viewModel.togglePlanTask(entry.day, task) }
                )
            }
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PlanMap(
    plan: List<PlanDay>,
    currentDay: Int,
    stateOf: (Int) -> NodeState,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    bubble: @Composable (PlanDay) -> Unit
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val count = plan.size
    val heightDp = (count * STEP_DP + 300).dp
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val transition = rememberInfiniteTransition(label = "map")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val bob by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val walk by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Restart),
        label = "walk"
    )

    val hero = remember { Animatable((currentDay - 1).coerceIn(0, count - 1).toFloat()) }
    val moving = hero.isRunning

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(VIEWPORT_DP.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Brush.linearGradient(listOf(primary, secondary)), RoundedCornerShape(22.dp))
    ) {
        // Taken here: inside the nested Box the outer scope's maxWidth is not reachable implicitly.
        val widthDp = maxWidth
        val widthPx = with(density) { widthDp.toPx() }
        val heightPx = with(density) { heightDp.toPx() }
        val unit = density.density
        val points = remember(widthPx, heightPx, count) { layoutPoints(count, widthPx, heightPx, unit) }

        // Start with today's point in the middle of the window, like a game camera.
        LaunchedEffect(points) {
            val focus = points[(currentDay - 1).coerceIn(0, count - 1)].center.y
            scroll.scrollTo(max(0f, focus - with(density) { VIEWPORT_DP.dp.toPx() } / 2f).toInt())
        }
        // The figure walks to a point that is unlocked; a locked one only opens its bubble.
        LaunchedEffect(selected) {
            val day = selected
            if (day != null && day <= currentDay) hero.animateTo((day - 1).toFloat(), tween(900))
        }

        androidx.compose.foundation.layout.Box(modifier = Modifier.verticalScroll(scroll)) {
            Canvas(
                modifier = Modifier
                    .width(widthDp)
                    .height(heightDp)
                    .pointerInput(points, selected) {
                        detectTapGestures { tap ->
                            val hit = points.indices.minByOrNull { (points[it].center - tap).getDistance() }
                            val near = hit != null && (points[hit].center - tap).getDistance() <= points[hit].radius * 1.9f
                            onSelect(if (near && hit != null) hit + 1 else null)
                        }
                    }
            ) {
                drawWorld(size, points, primary, secondary)
                drawRoute(points, currentDay, stateOf, count)
                points.forEachIndexed { index, point ->
                    drawNode(point, index + 1, stateOf(index + 1), index + 1 == selected, pulse, measurer)
                }
                val position = heroPosition(points, hero.value)
                drawHero(position.first, position.second, bob, if (moving) walk else 0f, moving)
            }

            val day = selected
            if (day != null && day in 1..count) {
                val point = points[day - 1]
                Popup(
                    popupPositionProvider = object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize
                        ): IntOffset {
                            val gap = (point.radius * 1.6f).toInt()
                            val x = (anchorBounds.left + point.center.x - popupContentSize.width / 2f).toInt()
                                .coerceIn(0, max(0, windowSize.width - popupContentSize.width))
                            val above = anchorBounds.top + point.center.y - popupContentSize.height - gap
                            val y = if (above >= 0f) above.toInt() else (anchorBounds.top + point.center.y + gap).toInt()
                            return IntOffset(x, y)
                        }
                    },
                    onDismissRequest = { onSelect(null) },
                    properties = PopupProperties(focusable = true)
                ) {
                    Card(
                        modifier = Modifier.width(300.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
                        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Neon.Cyan, Neon.Magenta)))
                    ) {
                        bubble(plan[day - 1])
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBubble(
    entry: PlanDay,
    language: String,
    unlocked: Boolean,
    taskDone: (String) -> Boolean,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = stringResource(R.string.plan_day_of, entry.day),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (!unlocked) {
            Text(
                text = stringResource(R.string.plan_locked_day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        TaskRow(stringResource(R.string.plan_task_behavior), entry.behavior(language), taskDone("b"), unlocked) { onToggle("b") }
        TaskRow(stringResource(R.string.plan_task_spiritual), entry.spiritual(language), taskDone("s"), unlocked) { onToggle("s") }
        TaskRow(stringResource(R.string.plan_task_challenge), entry.challenge(language), taskDone("c"), unlocked) { onToggle("c") }
    }
}

@Composable
private fun TaskRow(label: String, text: String, done: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Checkbox(checked = done, onCheckedChange = { onToggle() }, enabled = enabled)
        Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---- geometry ----

private const val STEP_DP = 118
private const val VIEWPORT_DP = 560

private class MapPoint(val center: Offset, val radius: Float, val depth: Float)

/** Day 1 sits at the bottom and nearest; day N near the horizon, smaller and closer to the middle. */
private fun layoutPoints(count: Int, width: Float, height: Float, unit: Float): List<MapPoint> {
    val bottom = 150f * unit
    val top = 190f * unit
    return List(count) { index ->
        val t = if (count == 1) 0f else index / (count - 1).toFloat()
        val depth = 1f - 0.45f * t
        val y = height - bottom - t * (height - top - bottom)
        val amplitude = width * 0.30f * (0.55f + 0.45f * depth)
        val x = width / 2f + sin(index * 0.85f) * amplitude
        MapPoint(Offset(x, y), 26f * unit * depth, depth)
    }
}

/** Position and scale of the figure for a fractional index between two points. */
private fun heroPosition(points: List<MapPoint>, value: Float): Pair<Offset, Float> {
    val low = value.toInt().coerceIn(0, points.size - 1)
    val high = (low + 1).coerceAtMost(points.size - 1)
    val f = (value - low).coerceIn(0f, 1f)
    val a = points[low]
    val b = points[high]
    val x = a.center.x + (b.center.x - a.center.x) * f
    val y = a.center.y + (b.center.y - a.center.y) * f
    val scale = a.depth + (b.depth - a.depth) * f
    return Offset(x, y) to scale
}

// ---- drawing ----

private fun DrawScope.drawWorld(size: Size, points: List<MapPoint>, primary: Color, secondary: Color) {
    val horizon = points.last().center.y - 120f * density
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF1A0B3D), Color(0xFF0B1230), Color(0xFF070A1A)),
            startY = 0f, endY = size.height
        )
    )
    // A glow on the horizon and a few distant stars.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, secondary.copy(alpha = 0.35f), Color.Transparent),
            startY = horizon - 90f * density, endY = horizon + 60f * density
        ),
        topLeft = Offset(0f, horizon - 90f * density),
        size = Size(size.width, 150f * density)
    )
    for (i in 0 until 26) {
        val sx = (i * 97 % 100) / 100f * size.width
        val sy = ((i * 53 + 11) % 100) / 100f * (horizon - 20f * density).coerceAtLeast(10f)
        drawCircle(Color.White.copy(alpha = 0.25f + (i % 4) * 0.1f), radius = (1f + (i % 3)) * density, center = Offset(sx, sy))
    }
    // Perspective floor: rows get closer together towards the horizon, columns meet at one point.
    val rows = 16
    for (k in 1..rows) {
        val f = (k / rows.toFloat())
        val y = horizon + (size.height - horizon) * f * f
        drawLine(primary.copy(alpha = 0.05f + 0.12f * f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f * density)
    }
    val vanishing = Offset(size.width / 2f, horizon)
    for (k in -8..8) {
        val bottomX = size.width / 2f + k * size.width * 0.28f
        drawLine(primary.copy(alpha = 0.10f), vanishing, Offset(bottomX, size.height), strokeWidth = 1f * density)
    }
}

private fun DrawScope.drawRoute(points: List<MapPoint>, currentDay: Int, stateOf: (Int) -> NodeState, count: Int) {
    for (i in 0 until count - 1) {
        val a = points[i]
        val b = points[i + 1]
        val reached = i + 1 < currentDay || stateOf(i + 1) == NodeState.DONE
        val width = 14f * density * (a.depth + b.depth) / 2f
        val path = Path().apply {
            moveTo(a.center.x, a.center.y)
            val midY = (a.center.y + b.center.y) / 2f
            cubicTo(a.center.x, midY, b.center.x, midY, b.center.x, b.center.y)
        }
        val color = if (reached) Neon.Cyan else Neon.Violet
        if (reached) {
            drawPath(path, color.copy(alpha = 0.10f), style = Stroke(width * 2.6f, cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = 0.22f), style = Stroke(width * 1.6f, cap = StrokeCap.Round))
            drawPath(path, color, style = Stroke(width * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawPath(
                path, color.copy(alpha = 0.55f),
                style = Stroke(width * 0.45f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f * density, 14f * density)))
            )
        }
    }
}

private fun DrawScope.drawNode(
    point: MapPoint,
    day: Int,
    state: NodeState,
    selected: Boolean,
    pulse: Float,
    measurer: androidx.compose.ui.text.TextMeasurer
) {
    val base = when (state) {
        NodeState.DONE -> Neon.Lime
        NodeState.TODAY -> Neon.Cyan
        NodeState.MISSED -> Neon.Amber
        NodeState.LOCKED -> Neon.Violet.copy(alpha = 0.6f)
    }
    val c = point.center
    val r = point.radius
    // Cast shadow under the sphere, which is what makes it sit on the floor.
    drawOval(Color.Black.copy(alpha = 0.45f), Offset(c.x - r * 1.1f, c.y + r * 0.7f), Size(r * 2.2f, r * 0.7f))
    drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.45f), Color.Transparent), c, r * 2.6f), r * 2.6f, c)
    if (state == NodeState.TODAY) {
        val ring = r * (1.3f + pulse * 1.3f)
        drawCircle(base.copy(alpha = (1f - pulse) * 0.7f), ring, c, style = Stroke(2.5f * density))
    }
    // The sphere: a radial gradient whose highlight sits up and to the left.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.95f), base, base.copy(alpha = 0.55f).compositeOver(Color(0xFF0B1230))),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
            radius = r * 1.5f
        ),
        radius = r, center = c
    )
    drawCircle(Color.White.copy(alpha = if (selected) 0.95f else 0.35f), r, c, style = Stroke((if (selected) 3f else 1.5f) * density))
    if (state == NodeState.DONE) {
        val check = Path().apply {
            moveTo(c.x - r * 0.45f, c.y)
            lineTo(c.x - r * 0.1f, c.y + r * 0.35f)
            lineTo(c.x + r * 0.5f, c.y - r * 0.35f)
        }
        drawPath(check, Color(0xFF05210F), style = Stroke(r * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    } else {
        val style = TextStyle(color = Color(0xFF06101F), fontWeight = FontWeight.Bold, fontSize = (r * 0.85f).toSp())
        val layout = measurer.measure(day.toString(), style)
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f
    )
}

/** The figure: shadow, legs, body, arms, head with a visor and a halo, all shaded to look solid. */
private fun DrawScope.drawHero(foot: Offset, depth: Float, bob: Float, walk: Float, moving: Boolean) {
    val s = density * depth * 1.05f
    val lift = (if (moving) 0f else bob * 3f) * s
    val swing = if (moving) sin(walk) * 5f * s else 0f
    val top = foot.y - 78f * s - lift
    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), Offset(foot.x, foot.y), 30f * s), Offset(foot.x - 26f * s, foot.y - 6f * s), Size(52f * s, 14f * s))
    drawCircle(Brush.radialGradient(listOf(Neon.Cyan.copy(alpha = 0.35f), Color.Transparent), foot, 40f * s), 40f * s, foot)
    // Legs.
    for (side in listOf(-1, 1)) {
        val dy = swing * side
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF3A4A9A), Color(0xFF151C45)), foot.y - 30f * s, foot.y),
            Offset(foot.x + side * 8f * s - 5f * s, foot.y - 30f * s - lift),
            Size(10f * s, 28f * s + dy),
            CornerRadius(5f * s)
        )
    }
    // Arms swing against the legs.
    for (side in listOf(-1, 1)) {
        val dy = -swing * side
        drawRoundRect(
            Brush.verticalGradient(listOf(Neon.Violet, Color(0xFF3A1F8A)), top + 30f * s, top + 62f * s),
            Offset(foot.x + side * 22f * s - 4.5f * s, top + 30f * s + dy),
            Size(9f * s, 26f * s),
            CornerRadius(5f * s)
        )
    }
    // Body with a rim light on one edge.
    val bodyTop = top + 26f * s
    drawRoundRect(
        Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet), Offset(foot.x - 17f * s, bodyTop), Offset(foot.x + 17f * s, bodyTop + 44f * s)),
        Offset(foot.x - 17f * s, bodyTop), Size(34f * s, 44f * s), CornerRadius(14f * s)
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.35f), Offset(foot.x - 17f * s, bodyTop), Size(34f * s, 44f * s), CornerRadius(14f * s),
        style = Stroke(1.4f * s)
    )
    drawRoundRect(Neon.Magenta.copy(alpha = 0.9f), Offset(foot.x - 9f * s, bodyTop + 18f * s), Size(18f * s, 4f * s), CornerRadius(2f * s))
    // Head.
    val head = Offset(foot.x, top + 14f * s)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White, Color(0xFFB9ECFF), Color(0xFF5B6CFF)),
            Offset(head.x - 5f * s, head.y - 6f * s), 22f * s
        ),
        16f * s, head
    )
    drawRoundRect(Color(0xFF06101F), Offset(head.x - 12f * s, head.y - 5f * s), Size(24f * s, 11f * s), CornerRadius(5.5f * s))
    drawCircle(Neon.Cyan, 2.2f * s, Offset(head.x - 5f * s, head.y + 0.5f * s))
    drawCircle(Neon.Cyan, 2.2f * s, Offset(head.x + 5f * s, head.y + 0.5f * s))
    // Halo.
    drawOval(Neon.Magenta.copy(alpha = 0.85f), Offset(head.x - 13f * s, head.y - 27f * s), Size(26f * s, 7f * s), style = Stroke(1.8f * s))
}
