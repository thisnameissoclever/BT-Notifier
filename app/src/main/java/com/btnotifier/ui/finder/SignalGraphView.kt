package com.btnotifier.ui.finder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.btnotifier.R

class SignalGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var samples: List<SignalSample> = emptyList()
    private val signalPath = Path()
    private val gridFractions = floatArrayOf(0f, 0.5f, 1f)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface)
        alpha = 70
        strokeWidth = resources.displayMetrics.density
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        contentDescription = context.getString(R.string.finder_graph, 0, context.getString(R.string.finder_signal_none))
    }

    fun setSignal(snapshot: FinderSignalSnapshot, description: String) {
        samples = snapshot.samples
        contentDescription = context.getString(R.string.finder_graph, samples.size, description)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 12f * resources.displayMetrics.density
        val plotWidth = (width - 2 * inset).coerceAtLeast(1f)
        val plotHeight = (height - 2 * inset).coerceAtLeast(1f)
        for (fraction in gridFractions) {
            val y = inset + fraction * plotHeight
            canvas.drawLine(inset, y, width - inset, y, grid)
        }
        if (samples.isEmpty()) return
        val firstAt = samples.first().at
        val span = (samples.last().at - firstAt).coerceAtLeast(1L)
        fun x(sample: SignalSample) = inset + ((sample.at - firstAt).toDouble() / span * plotWidth).toFloat()
        fun y(sample: SignalSample) = inset + ((-30.0 - sample.rssi.coerceIn(-110.0, -30.0)) / 80.0 * plotHeight).toFloat()
        signalPath.reset()
        samples.forEachIndexed { index, sample ->
            if (index == 0) signalPath.moveTo(x(sample), y(sample)) else signalPath.lineTo(x(sample), y(sample))
        }
        canvas.drawPath(signalPath, line)
        val last = samples.last()
        canvas.drawCircle(x(last), y(last), 4f * resources.displayMetrics.density, line)
    }
}
