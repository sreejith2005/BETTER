package dev.better.app.data

import androidx.room.withTransaction
import dev.better.app.domain.*
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class LocalRepository(val database: BetterDatabase, private val clock: AppClock) {
    val settings = database.foundation().observeSettings()
    val standards = database.foundation().observeStandards()

    suspend fun onboard(input: SetupInput) {
        require(input.validate() == null) { input.validate().orEmpty() }
        database.withTransaction {
            val dao = database.foundation()
            if (dao.settings() != null) return@withTransaction
            val timestamp = clock.now().toEpochMilli()
            val profile = UUID.randomUUID().toString()
            dao.insertSettings(AppSettings(
                profileId = profile, dayBoundary = input.boundary, zoneId = input.zone,
                workStart = input.workStart, workEnd = input.workEnd,
                careerStart = input.careerStart, careerEnd = input.careerEnd,
                notificationsWanted = input.notificationsWanted, createdAt = timestamp,
            ))
            val careerId = UUID.randomUUID().toString()
            dao.insertCommitment(Commitment(careerId, "Career Focus", "CAREER", essential = true))
            dao.insertStandard(CareerStandard(UUID.randomUUID().toString(), careerId,
                operationalDate(clock.now(), ZoneId.of(input.zone), LocalTime.parse(input.boundary)).toString(),
                targetSeconds = 30 * 60, createdAt = timestamp))
            dao.appendLog(ActionLog(UUID.randomUUID().toString(), "AppSettings", profile, "ONBOARD",
                "Local profile created; Career standard 30 minutes; day begins ${input.boundary}; timezone ${input.zone}", timestamp))
        }
    }
}
