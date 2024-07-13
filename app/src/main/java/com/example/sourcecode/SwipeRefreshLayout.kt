package com.example.sourcecode

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class SwipeRefreshLayout(context: Context, attributeSet: AttributeSet): ViewGroup(context,attributeSet) {

    open inner class RefreshView(context: Context) : View(context) {

        private var size = 100
        private var colour = Color.RED
        private var drawFunc: RefreshView.(canvas: Canvas) -> Unit = {
            it.drawCircle(
                size / 2f,
                size / 2f,
                size / 2f,
                Paint().apply { color = colour })
        }
        private var refreshAnimator: ObjectAnimator =
            ObjectAnimator.ofFloat(this, "alpha", 1f, 0f, 1f).apply {
                duration = 2000
                repeatCount = -1
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(size, size)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawFunc(canvas)
        }

        fun setColour(colour: Int) {
            this.colour = colour
        }

        fun setSize(size: Int) {
            this.size = size
        }

        fun setDrawFunction(func: RefreshView.(canvas: Canvas) -> Unit) {
            this.drawFunc = func
        }

        fun setRefreshAnimator(animator: ObjectAnimator) {
            refreshAnimator = animator.apply { repeatCount = -1 }
        }

        fun startRefreshAnimate() {
            refreshAnimator.start()
        }

        fun stopRefreshAnimate() {
            refreshAnimator.pause()
        }
    }

    private var isRefreshAllowed = true
    private var isRefreshing = false

    private var maxMoveDistance = 600
    private var finalMoveDistance = 400
    private var nowMoveDistance = 0

    private var maxScrollLength = 1000f
    private var downPositionY = -1f

    private var refreshView = RefreshView(context)
    private var refreshFunc: SwipeRefreshLayout.() -> Unit = {}

    init {
        addView(refreshView, 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildren(widthMeasureSpec, heightMeasureSpec)

        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(children.sumOf { it.height }, heightMeasureSpec)
        )
    }

    override fun onLayout(p0: Boolean, p1: Int, p2: Int, p3: Int, p4: Int) {
        var top = 0
        for (i in children.drop(1)) {
            i.layout(0, top, i.measuredWidth, top + i.measuredHeight)
            top += i.measuredHeight
        }
        refreshView.layout(
            (p3 - p1 - refreshView.measuredWidth) / 2, nowMoveDistance - refreshView.measuredHeight,
            (p3 - p1 + refreshView.measuredWidth) / 2, nowMoveDistance
        )
    }

    override fun isChildrenDrawingOrderEnabled(): Boolean = true

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        if (drawingPosition == 0) childCount - 1
        else drawingPosition - 1

    override fun canScrollVertically(direction: Int): Boolean = direction == -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (isRefreshing || !isRefreshAllowed) return false
        when (event!!.action) {
            MotionEvent.ACTION_MOVE -> {
                nowMoveDistance =
                    if (event.y - downPositionY > maxScrollLength) maxMoveDistance
                    else ((event.y - downPositionY) * maxMoveDistance / maxScrollLength).toInt()
                refreshView.y = nowMoveDistance.toFloat()
                refreshView.alpha = if (nowMoveDistance > finalMoveDistance) 1f
                else nowMoveDistance / finalMoveDistance.toFloat()
            }

            MotionEvent.ACTION_UP -> CoroutineScope(Dispatchers.Main).launch {
                if ((event.y - downPositionY) * maxMoveDistance > finalMoveDistance * maxScrollLength) {
                    refreshView.animate().y(finalMoveDistance.toFloat())
                    refreshView.startRefreshAnimate()
                    isRefreshing = true

                    delay(Random.nextLong(1000, 5000))
                    refreshFunc()

                    isRefreshing = false
                    refreshView.stopRefreshAnimate()
                }
                refreshView.animate().y(0f).alpha(0f)
            }
        }
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean =
        when (ev!!.action) {
            MotionEvent.ACTION_DOWN -> {
                downPositionY = ev.y
                false
            }

            MotionEvent.ACTION_MOVE -> {
                if (ev.y > downPositionY) !canChildScroll(
                    ev.x, ev.y
                ) && !isRefreshing && isRefreshAllowed else false
            }

            else -> false
        }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    private fun canChildScroll(x: Float, y: Float): Boolean {
        for (i in children)
            if (i.left < x && x < i.right &&
                i.top < y && y < i.bottom
            )
                return i.canScrollVertically(-1)
        return false
    }

    fun setRefreshFunction(func: SwipeRefreshLayout.() -> Unit) {
        refreshFunc = func
    }

    fun setAllowedRefresh(boolean: Boolean) {
        isRefreshAllowed = boolean
    }

    fun setMaxMoveDistance(maxMoveDistance: Int) {
        this.maxMoveDistance = maxMoveDistance
    }

    fun setFinalMoveDistance(finalMoveDistance: Int) {
        this.finalMoveDistance = finalMoveDistance
    }

    fun setMaxScrollLength(maxScrollLength: Float) {
        this.maxScrollLength = maxScrollLength
    }

    fun setRefreshView(refreshView: RefreshView) {
        this.refreshView = refreshView
    }
}