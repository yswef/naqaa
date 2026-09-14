package com.naqaa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.naqaa.app.R
import com.naqaa.app.data.Persona

/**
 * The mark shown inside the application so the chosen launcher entry is recognisable
 * without opening the launcher: the same drawing the alias uses on the home screen.
 */
@Composable
fun PersonaMark(persona: Persona, modifier: Modifier = Modifier, size: Int = 48) {
    val background = when (persona) {
        Persona.NAQAA -> Color(0xFF176B58)
        Persona.CALCULATOR -> Color(0xFF3B4A56)
        Persona.NOTES -> Color(0xFF8A6A3B)
        Persona.TASKS -> Color(0xFF4A5D3A)
    }
    val icon = when (persona) {
        Persona.NAQAA -> R.drawable.ic_launcher_foreground
        Persona.CALCULATOR -> R.drawable.ic_launcher_calculator_foreground
        Persona.NOTES -> R.drawable.ic_launcher_notes_foreground
        Persona.TASKS -> R.drawable.ic_launcher_tasks_foreground
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(background)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size.dp)
        )
    }
}
