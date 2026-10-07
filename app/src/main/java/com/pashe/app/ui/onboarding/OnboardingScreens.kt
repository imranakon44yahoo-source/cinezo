package com.pashe.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pashe.app.R
import com.pashe.app.ui.components.SectionCard
import com.pashe.app.ui.theme.PasheColors

@Composable
private fun Brand() {
    Text("🤲", fontSize = 56.sp)
    Text(stringResource(R.string.app_name), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = PasheColors.TealDark)
    Text(
        stringResource(R.string.tagline), fontSize = 20.sp, color = PasheColors.InkMuted, textAlign = TextAlign.Center,
    )
}

/** Shown once before anything else; always in Bengali since no language has been chosen yet. */
@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PasheColors.Background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Brand()
        Spacer(Modifier.height(24.dp))
        SectionCard {
            Text(stringResource(R.string.disclaimer_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PasheColors.Missed)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.disclaimer_body), fontSize = 20.sp, lineHeight = 30.sp, color = PasheColors.Ink)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAccept,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text(stringResource(R.string.disclaimer_accept), fontSize = 22.sp) }
    }
}

@Composable
fun ModeSelectScreen(onChild: () -> Unit, onParent: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PasheColors.Background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Brand()
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.mode_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = PasheColors.Ink)
        Spacer(Modifier.height(20.dp))
        ModeButton("👨‍👩‍👦", stringResource(R.string.mode_child), stringResource(R.string.mode_child_desc), PasheColors.Teal, onChild)
        Spacer(Modifier.height(20.dp))
        ModeButton("👵", stringResource(R.string.mode_parent), stringResource(R.string.mode_parent_desc), PasheColors.Taken, onParent)
    }
}

@Composable
private fun ModeButton(emoji: String, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(emoji, fontSize = 40.sp)
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 18.sp, textAlign = TextAlign.Center)
        }
    }
}
