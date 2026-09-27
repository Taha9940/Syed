package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SyedCyan
import com.example.ui.theme.SyedNavy

@Composable
fun TahaLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 64.dp,
    showTagline: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Column(
        modifier = modifier.testTag("taha_logo_container"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * 0.28f))
                .background(Color(0xFF0F172A))
                .border(
                    width = 1.5.dp,
                    color = Color(0xFFFFD700).copy(alpha = 0.5f), // Yellow accent from Taha logo
                    shape = RoundedCornerShape(iconSize * 0.28f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.taha_logo),
                contentDescription = "Taha Logo",
                modifier = Modifier
                    .size(iconSize * 0.88f)
                    .clip(RoundedCornerShape(iconSize * 0.22f)),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Taha",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            ),
            color = textColor
        )

        if (showTagline) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.tagline),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = SyedCyan
            )
        }
    }
}

/**
 * Backward compatibility alias for TahaLogo
 */
@Composable
fun SyedLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 64.dp,
    showTagline: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onBackground
) {
    TahaLogo(
        modifier = modifier,
        iconSize = iconSize,
        showTagline = showTagline,
        textColor = textColor
    )
}
