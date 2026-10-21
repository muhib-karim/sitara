package app.sitara.music

import android.content.Intent
import android.os.Build
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
        // Otherwise Android keeps a grey navigation bar scrim over the dock once the keyboard has closed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
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
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) vm.openPlayer()
        sharedRequest(intent.action, intent.dataString, intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())?.let(vm::open)
    }

    companion object {
        /** Set by the home-screen widget: open Now Playing. */
        const val EXTRA_OPEN_PLAYER = "app.sitara.music.extra.OPEN_PLAYER"
    }
}
