package dev.better.app.probe

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.feature.*
import dev.better.app.platform.AndroidClock
import kotlinx.coroutines.*
import org.json.*
import java.io.File
import java.time.LocalTime

/** Real-clock lifecycle checks use only synthetic data in a separate debug database. */
object FocusProbe {
    const val DATABASE = "phase4-focus-probe.db"
    private var repository: FocusRepository? = null
    fun repo(context: android.content.Context): FocusRepository {
        if (repository == null) repository = FocusRepository(BetterDatabase.open(context.applicationContext,DATABASE),AndroidClock(),
            { Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1) })
        return repository!!
    }
    suspend fun write(context: android.content.Context, repo: FocusRepository, label: String) {
        val state=repo.snapshot()
        val payload=JSONObject().put("label",label).put("wall",repo.clock.now().toString()).put("elapsed",repo.clock.elapsedMillis())
            .put("boot",Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1))
            .put("sessions",JSONArray(state.sessions.map { s -> JSONObject().put("id",s.id).put("state",s.state).put("millis",state.sessionMillis(s.id)).put("recovery",s.recovery) }))
            .put("segments",JSONArray(state.segments.map { s -> JSONObject().put("session",s.sessionId).put("startElapsed",s.startElapsed)
                .put("startWall",s.startWall).put("millis",s.durationMillis).put("candidate",s.candidateMillis ?: JSONObject.NULL).put("closed",s.closed) }))
        File(context.filesDir,"phase4-focus-probe.json").writeText(payload.toString(2))
        if(label!="tick") File(context.filesDir,"phase4-focus-trace.jsonl").appendText(payload.toString()+"\n")
    }
}
class FocusProbeActivity : ComponentActivity() {
    private val repo by lazy { FocusProbe.repo(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            LocalRepository(repo.db,repo.clock).onboard(SetupInput(zone=repo.clock.zone().id))
            command(intent)
            setContent {
                val model: CareerViewModel = viewModel(factory=object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=CareerViewModel(repo) as T
                })
                MaterialTheme { Surface { Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text("PHASE 4 · TEST DATA",style=MaterialTheme.typography.titleLarge)
                    CareerToday(model, operationalDate(repo.clock.now(),repo.clock.zone(),LocalTime.of(4,0)))
                } } }
            }
            while(isActive) { FocusProbe.write(this@FocusProbeActivity,repo,"tick");delay(1000) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);lifecycleScope.launch { command(intent) } }
    private suspend fun command(intent: Intent) {
        val action=intent.getStringExtra("command") ?: "inspect"
        if(action=="start" || action=="rescue") repo.start("Portfolio/project",action=="rescue",true,"Synthetic fixture","Timer test")
        else if(action!="inspect") repo.snapshot().active?.let { repo.act(it.id,action.uppercase()) }
        FocusProbe.write(this,repo,action)
    }
}
