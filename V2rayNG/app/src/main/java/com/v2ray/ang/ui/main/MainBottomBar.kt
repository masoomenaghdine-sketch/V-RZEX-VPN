package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val connectedColor = Color(0xFF30D158)
    val disconnectedColor = if (isDarkTheme) Color(0xFF5E5CE6) else Color(0xFF5856D6)

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
                .semantics { contentDescription = displayText },
            color = MaterialTheme.colorScheme.onSurface
        )
        Button(
            onClick = { onAction(MainAction.ToggleService) },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) connectedColor else disconnectedColor,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
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
                Box(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = if (isRunning) "قطع اتصال" else "اتصال امن",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
