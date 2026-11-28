@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ky7ra.cpkeditor.R

/**
 * First-run screen: asks who the user is. The hero card shows the image named
 * `welcome_character` from res/drawable-nodpi when the project contains one, and an
 * Versixty logo otherwise.
 */
@Composable
fun OnboardingScreen(onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
   ) {
        HeroCard(Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(cs.primary))
            Box(Modifier.width(26.dp).height(5.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.5f)))
            Box(Modifier.width(14.dp).height(5.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.onb_kicker),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
        )
        Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.onb_hint)) },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onDone(name.trim()) }),
        )
        Spacer(Modifier.height(14.dp))
        SlideToAct(
            label = stringResource(R.string.onb_slide),
            enabled = name.isNotBlank(),
            onComplete = { onDone(name.trim()) },
        )
        if (name.isBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onb_need_name),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HeroCard(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val welcome = rememberWelcomeImage()
    val spin = rememberInfiniteTransition(label = "float")
    val angle by spin.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "angle",
    )

    Box(modifier.clip(RoundedCornerShape(36.dp)).background(cs.surfaceVariant)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Brand.Orange, radius = w * 0.56f, center = Offset(w * 0.22f, h * 0.55f))
            drawCircle(
                lerp(Brand.Orange, Color.White, 0.14f),
                radius = w * 0.46f,
                center = Offset(w * 0.86f, h * 0.62f),
            )
            drawCircle(Color.White.copy(alpha = 0.28f), radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.5f))
            drawCircle(Brand.Ink, radius = w * 0.05f, center = Offset(w * 0.86f, h * 0.2f))
        }

        if (welcome != null) {
            Image(
                painter = welcome,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize().padding(top = 24.dp),
            )
        } else {
            VersixtyMark(
                Modifier
                    .align(Alignment.Center)
                    .size(150.dp)
                    .graphicsLayer { rotationZ = angle },
                color = Brand.Ink,
            )
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Brand.Ink),
            contentAlignment = Alignment.Center,
       ) {
            Text("忍", color = Brand.Orange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The optional welcome picture: any image saved as res/drawable-nodpi/welcome_character.(png|webp|jpg). */
@Composable
private fun rememberWelcomeImage(): Painter? {
    val context = LocalContext.current
    val id = remember(context) {
        context.resources.getIdentifier("welcome_character", "drawable", context.packageName)
    }
    return if (id != 0) painterResource(id) else null
}
