package com.mobdev.catgram.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mobdev.catgram.MainActivity
import com.mobdev.catgram.R

@SuppressLint("ResourceType")
@Composable
fun StartScreen(
    uiState: MainActivity.UiState,
    onGoogleSignInClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Round Shape Image
        Image(
            painter = painterResource(id = R.raw.catgram_logo), // Replace with your image resource
            contentDescription = "App Logo",
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title Text
        Text(
            text = stringResource(R.string.start_screen_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(160.dp))

        // Sign in with Google Button
        Button(
            onClick = {
                uiState.signInInProgress.value = true
                onGoogleSignInClick()
            },
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(36.dp),
            contentPadding = PaddingValues(16.dp),
            enabled = !uiState.signInInProgress.value
        ) {
            // Button Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.sign_in_with_google_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(id = R.drawable.google_logo), // Replace with your Google logo drawable
                    contentDescription = "Google Logo",
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterVertically),
                    tint = Color.Unspecified // Maintain original color
                )
            }
        }
    }
}

@Preview
@Composable
fun StartScreenPreview() {
    StartScreen(MainActivity.UiState()) { }
}