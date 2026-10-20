package app.sitara.music

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var vm: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this, AppViewModel.factory(application as SitaraApplication))[AppViewModel::class.java]
        // A recreated activity has already handled the intent that launched it.
        if (savedInstanceState == null) {
            open(intent)
            (application as SitaraApplication).songDownloads.resumeUnfinished()
        }
        val systemReducedMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent { SitaraRoot(vm, systemReducedMotion) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent) {
        sharedRequest(intent.action, intent.dataString, intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())?.let(vm::open)
    }
}
