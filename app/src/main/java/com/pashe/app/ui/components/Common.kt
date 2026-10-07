package com.pashe.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pashe.app.R
import com.pashe.app.domain.DoseStatus
import com.pashe.app.ui.theme.PasheColors

/** True unless the user switched the child UI to English. */
@Composable
fun isBengali(): Boolean = LocalConfiguration.current.locales[0].language != "en"

tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity in context")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasheTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PasheColors.Teal, titleContentColor = PasheColors.Surface,
            navigationIconContentColor = PasheColors.Surface, actionIconContentColor = PasheColors.Surface,
        ),
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PasheColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun StatusChip(status: DoseStatus, parentMode: Boolean = false, fontSize: TextUnit = 14.sp) {
    val (label, fg, bg) = when (status) {
        DoseStatus.TAKEN -> Triple(if (parentMode) R.string.parent_status_taken else R.string.status_taken, PasheColors.Taken, PasheColors.TakenBg)
        DoseStatus.PENDING -> Triple(if (parentMode) R.string.parent_status_pending else R.string.status_pending, PasheColors.Pending, PasheColors.PendingBg)
        DoseStatus.MISSED -> Triple(if (parentMode) R.string.parent_status_missed else R.string.status_missed, PasheColors.Missed, PasheColors.MissedBg)
    }
    Text(
        stringResource(label),
        color = fg,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = PasheColors.InkMuted,
        modifier = modifier.padding(24.dp),
    )
}
