package com.armorlab.securedroid.smoke

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.R
import com.armorlab.securedroid.SecureGuardApp
import com.armorlab.securedroid.realtime.SecureGuardAppRefs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 应用启动冒烟测试。
 *
 * 真实走一遍 Application.onCreate(通知渠道创建 + 定时任务同步),验证应用**能启动**。
 * 此前所有验证都止步于"能编译 / 能打包",这里补上运行时最后一环。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplicationSmokeTest {

    @Test
    fun applicationStartsAndRegistersNotificationChannels() {
        val app = ApplicationProvider.getApplicationContext<SecureGuardApp>()
        assertNotNull("Application 实例应存在", app)

        val nm = app.getSystemService(NotificationManager::class.java)
        assertNotNull("通知服务应可用", nm)

        val realtime = nm.getNotificationChannel(SecureGuardAppRefs.CHANNEL_REALTIME)
        val alert = nm.getNotificationChannel(SecureGuardAppRefs.CHANNEL_ALERT)
        assertNotNull("实时防护通知渠道必须创建", realtime)
        assertNotNull("威胁告警通知渠道必须创建", alert)
        assertTrue("告警渠道应为高优先级", alert.importance >= NotificationManager.IMPORTANCE_HIGH)
    }

    @Test
    fun appStringResourcesResolve() {
        val app = ApplicationProvider.getApplicationContext<SecureGuardApp>()
        // 抽查关键文案可解析(资源裁剪 / 语言配置出错会在此暴露)
        assertTrue(app.getString(R.string.app_name).isNotBlank())
        assertTrue(app.getString(R.string.realtime_title).isNotBlank())
        assertTrue(app.getString(R.string.threat_found_title).isNotBlank())
        assertTrue(app.getString(R.string.auto_disinfect_title).isNotBlank())
    }
}
