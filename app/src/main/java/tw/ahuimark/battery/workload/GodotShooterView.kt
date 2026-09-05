package tw.ahuimark.battery.workload

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import java.util.WeakHashMap

/** The one engine instance is paused between workloads and reused on the next
 * round. Destroying Godot at every Compose disposal can terminate its process. */
class GodotShooterView(context: Context) : FrameLayout(context) {
    private val activity = requireNotNull(context.findActivity())
    private val session = GodotShooterSession.forActivity(activity)
    private val status = TextView(context).apply {
        text = "正在載入 Godot 3D 戰場…"
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(16, 29, 44))
        gravity = android.view.Gravity.CENTER
    }
    private var attached = false
    init { addView(status, LayoutParams(-1, -1)) }

    fun updateElapsed(elapsedMs: Long) { session.bridge.updateElapsed(elapsedMs) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        post {
            if (!attached) return@post
            runCatching { session.attach(this) }.onFailure {
                status.text = "Godot 戰場載入失敗：${it.message}"
                Log.e("AhuimarkGodot", "Unable to start 3D workload", it)
            }
        }
    }

    override fun onDetachedFromWindow() {
        attached = false
        session.detach(this)
        super.onDetachedFromWindow()
    }

    fun release() { session.detach(this) }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class GodotShooterSession(private val owner: Activity) : GodotHost {
    private val engine = Godot.getInstance(owner)
    val bridge = BenchmarkGodotPlugin(engine)
    private var renderContainer: FrameLayout? = null
    private var target: GodotShooterView? = null
    private var active = false

    init {
        (owner as? LifecycleOwner)?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_RESUME -> if (target != null) resume()
                Lifecycle.Event.ON_DESTROY -> {
                    pause()
                    // Engine exit is owned by the Activity, never by an individual test round.
                    if (renderContainer != null) engine.onDestroy(this)
                    sessions.remove(owner)
                }
                else -> Unit
            }
        })
    }

    fun attach(view: GodotShooterView) {
        target = view
        if (renderContainer == null) {
            check(engine.initEngine(this, commandLine, setOf(bridge))) { "Godot engine initialization failed" }
            renderContainer = requireNotNull(engine.onInitRenderView(this))
        }
        val render = requireNotNull(renderContainer)
        (render.parent as? ViewGroup)?.removeView(render)
        view.addView(render, FrameLayout.LayoutParams(-1, -1))
        resume()
    }

    fun detach(view: GodotShooterView) {
        if (target !== view) return
        pause()
        (renderContainer?.parent as? ViewGroup)?.removeView(renderContainer)
        target = null
    }

    private fun resume() {
        if (active || renderContainer == null) return
        active = true
        engine.onStart(this)
        engine.onResume(this)
    }

    private fun pause() {
        if (!active) return
        active = false
        engine.onPause(this)
        engine.onStop(this)
    }

    override fun getActivity(): Activity = owner
    override fun getGodot(): Godot = engine
    override fun getCommandLine(): List<String> = listOf(
        "--main-pack", "res://prism_front.pck", "--rendering-method", "mobile",
        "--rendering-driver", "vulkan"
    )
    override fun onGodotMainLoopStarted() { Log.i("AhuimarkGodot", "Engine main loop started") }
    override fun onGodotForceQuit(instance: Godot) {
        owner.runOnUiThread { pause() }
        Log.e("AhuimarkGodot", "Engine requested exit")
    }

    companion object {
        private val sessions = WeakHashMap<Activity, GodotShooterSession>()
        fun forActivity(owner: Activity) = sessions.getOrPut(owner) { GodotShooterSession(owner) }
    }
}

class BenchmarkGodotPlugin(godot: Godot) : GodotPlugin(godot) {
    @Volatile private var elapsedMs = 0L
    @Volatile private var receivedAt = SystemClock.elapsedRealtime()
    override fun getPluginName() = "AhuimarkBenchmark"
    fun updateElapsed(value: Long) {
        elapsedMs = value.coerceAtLeast(0)
        receivedAt = SystemClock.elapsedRealtime()
    }
    @UsedByGodot fun elapsed_millis(): Long = elapsedMs +
        (SystemClock.elapsedRealtime() - receivedAt).coerceIn(0, 1000)
    @UsedByGodot fun scene_ready() { Log.i("AhuimarkGodot", "3D scene ready") }
}
