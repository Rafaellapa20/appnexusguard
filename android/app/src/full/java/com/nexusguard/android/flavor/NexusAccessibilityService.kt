package com.nexusguard.android.flavor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.nexusguard.android.domain.AccessibilityNodeSnapshot
import com.nexusguard.android.domain.ForceStopNodeLookupResult
import com.nexusguard.android.domain.HibernationAppStepStatus
import com.nexusguard.android.domain.HibernationCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NexusAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayContainer: LinearLayout? = null
    private var overlayLabelView: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstanceRef.set(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // O loop de hibernação consulta rootInActiveWindow de forma controlada apenas quando uma sessão está ativa.
    }

    override fun onInterrupt() {
        cancelRequested.set(true)
        hideOverlayOnMainThread()
    }

    override fun onDestroy() {
        hideOverlayOnMainThread()
        activeInstanceRef.compareAndSet(this, null)
        super.onDestroy()
    }

    fun showOrUpdateOverlayOnMainThread(currentIndex: Int, totalCount: Int) {
        mainHandler.post {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                if (overlayContainer == null) {
                    val density = resources.displayMetrics.density
                    val container = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(
                            (16 * density).toInt(),
                            (10 * density).toInt(),
                            (16 * density).toInt(),
                            (10 * density).toInt()
                        )
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 14f * density
                            setColor(Color.parseColor("#11151E"))
                            setStroke((1.5f * density).toInt(), Color.parseColor("#7C5CF6"))
                        }
                    }

                    val statusText = TextView(this).apply {
                        setTextColor(Color.parseColor("#E8ECF4"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        text = "A hibernar $currentIndex de $totalCount"
                    }

                    val cancelText = TextView(this).apply {
                        setTextColor(Color.parseColor("#22D3EE"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setPadding((14 * density).toInt(), 0, 0, 0)
                        text = "· Cancelar"
                        setOnClickListener {
                            cancelRequested.set(true)
                        }
                    }

                    container.addView(statusText)
                    container.addView(cancelText)

                    val params = WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        y = (56 * density).toInt()
                    }

                    wm.addView(container, params)
                    overlayContainer = container
                    overlayLabelView = statusText
                } else {
                    overlayLabelView?.text = "A hibernar $currentIndex de $totalCount"
                }
            } catch (_: Exception) {
                // Ignora se o overlay não puder ser desenhado
            }
        }
    }

    fun hideOverlayOnMainThread() {
        mainHandler.post {
            val container = overlayContainer ?: return@post
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeViewImmediate(container)
            } catch (_: Exception) {
            } finally {
                overlayContainer = null
                overlayLabelView = null
            }
        }
    }

    suspend fun forceStopSinglePackage(packageName: String, timeoutMs: Long = 3_000L): HibernationAppStepStatus {
        if (cancelRequested.get()) return HibernationAppStepStatus.FAILED

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            startActivity(intent)
        } catch (_: Exception) {
            return HibernationAppStepStatus.FAILED
        }

        val startTime = System.currentTimeMillis()
        var clickedForceStop = false

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (cancelRequested.get()) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return HibernationAppStepStatus.FAILED
            }

            delay(160L)
            val root = rootInActiveWindow ?: continue
            val flatNodes = mutableListOf<AccessibilityNodeInfo>()
            val snapshots = mutableListOf<AccessibilityNodeSnapshot>()
            collectNodes(root, flatNodes, snapshots)

            if (!clickedForceStop) {
                when (val lookup = HibernationCalculator.findForceStopNode(snapshots)) {
                    is ForceStopNodeLookupResult.ClickableForceStop -> {
                        val targetNode = flatNodes.getOrNull(lookup.nodeIndex)
                        if (targetNode != null && performClickOnNodeOrParent(targetNode)) {
                            clickedForceStop = true
                            delay(220L)
                        }
                    }
                    is ForceStopNodeLookupResult.AlreadyStoppedDisabled -> {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(120L)
                        return HibernationAppStepStatus.ALREADY_STOPPED
                    }
                    ForceStopNodeLookupResult.NotFound -> {
                        // Continua a aguardar até que o ecrã carregue ou expire o timeout de 3 s
                    }
                }
            } else {
                val confirmIdx = HibernationCalculator.findConfirmDialogNodeIndex(snapshots)
                if (confirmIdx != null) {
                    val confirmNode = flatNodes.getOrNull(confirmIdx)
                    if (confirmNode != null && performClickOnNodeOrParent(confirmNode)) {
                        delay(180L)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(120L)
                        return HibernationAppStepStatus.STOPPED
                    }
                }
            }
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(120L)
        return HibernationAppStepStatus.FAILED
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo?,
        rawList: MutableList<AccessibilityNodeInfo>,
        snapshots: MutableList<AccessibilityNodeSnapshot>
    ) {
        if (node == null) return
        val index = rawList.size
        rawList.add(node)
        snapshots.add(
            AccessibilityNodeSnapshot(
                index = index,
                viewIdResourceName = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                isClickable = node.isClickable,
                isEnabled = node.isEnabled
            )
        )
        val count = node.childCount
        for (i in 0 until count) {
            collectNodes(node.getChild(i), rawList, snapshots)
        }
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    companion object {
        private val activeInstanceRef = AtomicReference<NexusAccessibilityService?>(null)
        private val cancelRequested = AtomicBoolean(false)

        fun isRunning(context: Context): Boolean {
            if (activeInstanceRef.get() != null) return true
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val expectedComponent = "${context.packageName}/${NexusAccessibilityService::class.java.name}"
                val expectedShort = "${context.packageName}/.flavor.NexusAccessibilityService"
                enabledServices.contains(expectedComponent, ignoreCase = true) ||
                    enabledServices.contains(expectedShort, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

        fun requestCancelCurrentBatch() {
            cancelRequested.set(true)
        }

        suspend fun executeBatch(
            packages: List<String>,
            onProgress: (index: Int, total: Int, packageName: String, outcome: HibernationAppStepStatus) -> Unit
        ): Map<String, HibernationAppStepStatus> {
            val service = activeInstanceRef.get()
                ?: return packages.associateWith { HibernationAppStepStatus.FAILED }

            cancelRequested.set(false)
            val results = LinkedHashMap<String, HibernationAppStepStatus>()
            val total = packages.size

            try {
                packages.forEachIndexed { idx, pkg ->
                    if (cancelRequested.get()) {
                        results[pkg] = HibernationAppStepStatus.FAILED
                        onProgress(idx + 1, total, pkg, HibernationAppStepStatus.FAILED)
                    } else {
                        service.showOrUpdateOverlayOnMainThread(idx + 1, total)
                        val outcome = service.forceStopSinglePackage(pkg, timeoutMs = 3_000L)
                        results[pkg] = outcome
                        onProgress(idx + 1, total, pkg, outcome)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                service.hideOverlayOnMainThread()
            }

            return results
        }
    }
}
