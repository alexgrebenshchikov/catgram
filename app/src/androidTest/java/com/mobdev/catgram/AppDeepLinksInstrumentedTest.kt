package com.mobdev.catgram

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobdev.catgram.ui.AppDeepLink
import com.mobdev.catgram.ui.AppDeepLinks
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDeepLinksInstrumentedTest {
    @Test
    fun notificationUriResolvesToPost() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("catgram://post/post-123?activityId=activity-456"),
        )

        assertEquals(
            AppDeepLink.Post("post-123", "activity-456"),
            AppDeepLinks.fromIntent(intent),
        )
    }
}
