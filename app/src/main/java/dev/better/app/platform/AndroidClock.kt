package dev.better.app.platform

import android.os.SystemClock
import dev.better.app.domain.AppClock
import java.time.Instant
import java.time.ZoneId

class AndroidClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
}
