package com.mobdev.catgram

import android.util.Log
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.mobdev.catgram.worker.OpenAppReminderWorker

import org.junit.Test
import org.junit.runner.RunWith

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Before
import java.util.concurrent.TimeUnit

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class WorkersInstrumentationTest {
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    @Throws(Exception::class)
    fun testOpenAppReminderWorkerWithInitialDelay() {
        val request = OneTimeWorkRequestBuilder<OpenAppReminderWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()

        val workManager = WorkManager.getInstance(getApplicationContext())
        val testDriver = WorkManagerTestInitHelper.getTestDriver(getApplicationContext())
        workManager.enqueue(request).result.get()
        testDriver?.setInitialDelayMet(request.id)
        val workInfo = workManager.getWorkInfoById(request.id).get()

        assertThat(workInfo?.state, `is`(WorkInfo.State.SUCCEEDED))
    }
}