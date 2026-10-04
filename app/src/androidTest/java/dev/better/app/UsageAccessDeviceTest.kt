package dev.better.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.platform.AndroidUsage
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class UsageAccessDeviceTest {
    @Test fun actualPermissionAndObservation() {
        val args=InstrumentationRegistry.getArguments()
        val expected=args.getString("expectedUsage")
        Assume.assumeTrue(expected != null)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val usage=AndroidUsage(context)
        val now=Instant.now();val from=now.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val o=usage.observe("com.instagram.android",from,now.toEpochMilli())
        if (usage.allowed()) {
            val events = context.getSystemService(android.app.usage.UsageStatsManager::class.java).queryEvents(from - 86_400_000L, now.toEpochMilli())
            val event = android.app.usage.UsageEvents.Event()
            val counts = mutableMapOf<Int,Int>()
            val active = mutableSetOf<String>()
            val paired = mutableSetOf<String>()
            var missingToday = 0
            var packageOpen = false
            var packageMissingToday = 0
            var pairedMissingWithOther = 0
            var unmatched = 0; var unmatchedToday = 0; var stoppedActiveToday = 0
            var first = -1
            var last = -1
            while(events.hasNextEvent()) {
                events.getNextEvent(event)
                if(event.packageName != "com.instagram.android") continue
                if(first < 0) first=event.eventType
                last=event.eventType
                counts[event.eventType]=(counts[event.eventType] ?: 0)+1
                if(event.eventType==1) { paired.add(event.className ?: "unknown"); packageOpen=true }
                if(event.eventType==2) {
                    if(!paired.remove(event.className ?: "unknown") && event.timeStamp > from) { missingToday++; if(paired.isNotEmpty()) pairedMissingWithOther++ }
                    if(!packageOpen && event.timeStamp > from) packageMissingToday++
                    packageOpen=false
                }
                if(android.os.Build.VERSION.SDK_INT >= 29) {
                    if(event.eventType==1) active.add(event.className ?: "unknown")
                    if(event.eventType==2 && !active.remove(event.className ?: "unknown")) { unmatched++; if(event.timeStamp > from) unmatchedToday++ }; if(event.eventType==23 && active.remove(event.className ?: "unknown") && event.timeStamp > from) stoppedActiveToday++
                }
            }
            println("USAGE_DIAGNOSTIC selected-package event counts=$counts firstType=$first lastType=$last unmatchedPauses=$unmatched openActivities=${active.size} unmatchedToday=$unmatchedToday stoppedActiveToday=$stoppedActiveToday")
            println("USAGE_PAIRING noStopMissingToday=$missingToday noStopOpen=${paired.size} missingWithOther=$pairedMissingWithOther packageMissingToday=$packageMissingToday packageOpen=$packageOpen")
        }
        assertEquals("com.instagram.android",o.packageName);assertEquals(now.toEpochMilli(),o.observedAt)
        if(expected=="granted") {assertTrue(usage.allowed());assertNotNull("Open then leave Instagram to provide complete events: ${o.reason}",o.totalMillis)}
        else {assertFalse(usage.allowed());assertNull(o.totalMillis)}
        println("USAGE_DEVICE permission=${usage.allowed()} selected=${o.packageName} observedAt=${o.observedAt} totalMillis=${o.totalMillis} state=${o.reason}")
    }
    @Test fun localOnlyManifestBaseline() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val info=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS)
        val permissions=info.requestedPermissions.orEmpty().toList()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(permissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertEquals(0,context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}


