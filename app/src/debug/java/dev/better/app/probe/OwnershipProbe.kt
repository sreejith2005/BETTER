package dev.better.app.probe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.platform.AndroidClock
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.time.*

/** Explicit synthetic SAF gate. Never opens better.db or preservation directories. */
class OwnershipProbeActivity : ComponentActivity() {
    private lateinit var db: BetterDatabase
    private val name = "phase6-ownership-probe.db"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = BetterDatabase.open(this, name)
        val archive = ArchiveRepository(this,db)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var message by remember { mutableStateOf("Synthetic storage only. Personal data is never opened by this screen.") }
            var preview by remember { mutableStateOf<ArchiveRepository.Preview?>(null) }
            var confirmation by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            fun work(block: suspend () -> Unit) { scope.launch { busy = true; try { withContext(Dispatchers.IO) { block() } } catch(e:Exception) { message = "Error: ${e.message}" } finally { busy=false } } }
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) work {
                val raw=archive.export(); contentResolver.openOutputStream(uri,"wt")!!.use { it.write(raw.toByteArray()) }
                File(filesDir,"phase6-probe-export.json").writeText(raw)
                message="Synthetic archive exported via picker. Now delete synthetic records."
            } }
            val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) work {
                val raw=contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                preview=archive.preview(raw); message="Import preview: ${preview!!.counts}. No records replaced yet."
            } }
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("PHASE 6 · SYNTHETIC ONLY",style=MaterialTheme.typography.headlineSmall)
                Text(message)
                Button(enabled=!busy,onClick={work {
                    if(db.foundation().settings()==null) {
                        val clock=AndroidClock();LocalRepository(db,clock).onboard(SetupInput())
                        val schedules=ScheduleRepository(db,clock);schedules.ensureCareerSchedule()
                        schedules.save(CommitmentDraft("Synthetic archive check",ScheduleRule(RuleType.DAILY,LocalDate.now().plusDays(1),LocalTime.of(10,0)),instructions="Exact synthetic text. No treatment advice."),false,conflictsAcknowledged=true)
                    }; message="Synthetic fixture ready. Export to Downloads as BETTER-phase6-synthetic.json."
                }}) {Text("1. Prepare synthetic fixture")}
                Button(enabled=!busy,onClick={export.launch("BETTER-phase6-synthetic.json")}) {Text("2. Export synthetic archive")}
                OutlinedTextField(confirmation,{confirmation=it},label={Text("Type DELETE SYNTHETIC")})
                Button(enabled=!busy && confirmation=="DELETE SYNTHETIC",onClick={work {archive.deleteLocal("DELETE MY LOCAL DATA");message="Synthetic records deleted. Personal storage unchanged.";confirmation=""}}) {Text("3. Delete synthetic records only")}
                Button(enabled=!busy,onClick={import.launch(arrayOf("application/json"))}) {Text("4. Choose synthetic archive")}
                preview?.let { p -> Button(enabled=!busy,onClick={work {
                    archive.replace(p,true)
                    val expected=JSONObject(File(filesDir,"phase6-probe-export.json").readText()).getJSONObject("tables").toString()
                    val actual=JSONObject(archive.export()).getJSONObject("tables").toString()
                    check(expected==actual)
                    message="PASS: picker export → synthetic deletion → preview → restore. Every table matches."
                    File(filesDir,"phase6-probe-result.txt").writeText("${Instant.now()} $message")
                    preview=null
                }}) {Text("5. Confirm synthetic restore")} }
            }
        } } }
    }
    override fun onDestroy() { super.onDestroy(); db.close() }
}
