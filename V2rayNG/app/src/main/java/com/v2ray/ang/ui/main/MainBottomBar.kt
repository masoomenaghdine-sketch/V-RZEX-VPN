package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    // VØRZEX UI palette: cyan while connected, deep-slate with violet identity while idle.
    // Keep the original MainAction.ToggleService callback so connection behavior is unchanged.
    val accentCyan = Color(0xFF36E3D2)
    val connectedColor = accentCyan
    val disconnectedColor = if (isDarkTheme) Color(0xFF19172F) else Color(0xFF5856D6)
    val actionContentColor = if (isRunning) Color(0xFF04100F) else if (isDarkTheme) accentCyan else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppDivider()
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clickable { onAction(MainAction.TestCurrentServer) }
                .semantics { contentDescription = displayText },
            color = MaterialTheme.colorScheme.onSurface
        )
        Button(
            onClick = { onAction(MainAction.ToggleService) },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) connectedColor else disconnectedColor,
                contentColor = actionContentColor
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (isRunning) 4.dp else 1.dp,
                pressedElevation = 0.dp
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
                Text(
                    text = if (isRunning) "قطع اتصال" else "اتصال VPN",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}
