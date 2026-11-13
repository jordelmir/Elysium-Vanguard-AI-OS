package com.elysium.vanguard.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.ui.theme.SectionColorManager
import com.elysium.vanguard.ui.theme.TitanColors

/**
 * TITAN HEADER
 * The standard, premium header for all Elysium Vanguard screens.
 * Features:
 * - Back Navigation (optional)
 * - Section Title (customizable font/color)
 * - Integrated Color Customizer
 */
@Composable
fun TitanHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    sectionName: String, // e.g., "FILEMANAGER", "GALLERY"
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var showColorDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // BACK BUTTON
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TitanColors.AbsoluteWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // TITLE
        Text(
            text = title.uppercase(),
            color = TitanColors.AbsoluteWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.weight(1f)
        )

        // CUSTOM ACTIONS
        actions()

        Spacer(modifier = Modifier.width(8.dp))

        // COLOR CUSTOMIZER
        ColorCustomizerIcon(
            onClick = { showColorDialog = true }
        )
    }

    // COLOR SELECTION DIALOG
    if (showColorDialog) {
        ColorSelectionDialog(
            sectionName = sectionName,
            onColorSelected = { newColor, applyToAll ->
                SectionColorManager.updateAccent(sectionName, newColor, applyToAll)
            },
            onDismiss = { showColorDialog = false }
        )
    }
}
