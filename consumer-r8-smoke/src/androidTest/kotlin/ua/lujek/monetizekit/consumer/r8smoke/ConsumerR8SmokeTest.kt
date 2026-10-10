package ua.lujek.monetizekit.consumer.r8smoke

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsumerR8SmokeTest {
    @Test
    fun minified_consumer_keeps_and_registers_adapter_initializers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val initializerClasses = listOf(
            "ua.lujek.monetizekit.admob.AdMobAdapterInitializer",
            "ua.lujek.monetizekit.meta.MetaAdapterInitializer",
            "ua.lujek.monetizekit.unity.UnityAdapterInitializer",
            "ua.lujek.monetizekit.mediation.meta.MetaAdMobMediationInitializer",
        )

        initializerClasses.forEach { className ->
            assertNotNull(context.classLoader.loadClass(className))
            val provider = packageManager.getProviderInfo(
                ComponentName(context.packageName, className),
                0,
            )
            assertFalse("$className must not be exported", provider.exported)
        }
    }
}
