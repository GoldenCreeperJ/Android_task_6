package com.example.sourcecode

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.O)
class SCRecycleView(context: Context, attributeSet: AttributeSet): ViewGroup(context,attributeSet) {
    class SCRecycler {
        val scViewHolderList = mutableListOf<SCViewHolder>()
        var isInitial = false
    }

    abstract class SCViewHolder(val itemView: View)

    abstract class SCAdapter<out ViewHolder : SCViewHolder> {
        lateinit var scRecycleView: SCRecycleView
        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder
        abstract fun onBindingViewHolder(holder: @UnsafeVariance ViewHolder, position: Int)
        abstract fun getItemCount(): Int

        fun notifyDataSetChanged() {
            scRecycleView.removeAllViews()
            scRecycleView.recycler.scViewHolderList.clear()
            for (i in 0 until scRecycleView.adapter.getItemCount()) {
                val v = onCreateViewHolder(scRecycleView, 0)
                scRecycleView.recycler.scViewHolderList.add(v)
                scRecycleView.addView(v.itemView)
            }
        }

        fun notifyItemInserted(position: Int) {
            notifyItemRangeInserted(position, 1)
        }

        fun notifyItemRemoved(position: Int) {
            notifyItemRangeRemoved(position, 1)
        }

        fun notifyItemChanged(position: Int) {
            notifyItemRangeChanged(position, 1)
        }

        fun notifyItemMoved(fromPosition: Int, toPosition: Int) {
            scRecycleView.recycler.scViewHolderList.add(
                toPosition - 1,
                scRecycleView.recycler.scViewHolderList.removeAt(fromPosition)
            )
        }

        fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
            for (i in positionStart until positionStart + itemCount) {
                val v = onCreateViewHolder(scRecycleView, 0)
                scRecycleView.recycler.scViewHolderList.add(i, v)
                scRecycleView.addView(v.itemView)
            }
        }

        fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
            for (i in 0..itemCount) {
                scRecycleView.removeView(scRecycleView.recycler.scViewHolderList[positionStart].itemView)
                scRecycleView.recycler.scViewHolderList.removeAt(positionStart)
            }
        }

        fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) {
            for (i in positionStart until positionStart + itemCount) {
                onBindingViewHolder(
                    scRecycleView.recycler.scViewHolderList[i] as @UnsafeVariance ViewHolder, i
                )
            }
        }
    }

    class SCLayoutManager(context: Context) {
        companion object {
            const val VERTICAL = 0
            const val HORIZONTAL = 1
        }

        private var direction = 0
        private var distance = 0f
        private var endFlag = false
        private var isScrolling = false
        private var anchor = 0
        private var offset = 0f
        private lateinit var downPosition: Pair<Float, Float>
        private lateinit var scRecycleView: SCRecycleView
        var orientation = VERTICAL

        private fun orientationFuncCore(
            block1: SCLayoutManager.() -> Any?,
            block2: SCLayoutManager.() -> Any?
        ): Any? {
            var result: Any? = null
            when (orientation) {
                VERTICAL -> result = block1()
                HORIZONTAL -> result = block2()
            }
            return result
        }

        private fun mainFuncCore(
            block1: SCLayoutManager.(i: Int, start: Int) -> Any? = { _, _ -> },
            block2: SCLayoutManager.(i: Int, start: Int) -> Any? = { _, _ -> },
            block3: SCLayoutManager.(i: Int, start: Int) -> Any? = { _, _ -> },
            block4: SCLayoutManager.(i: Int, start: Int) -> Any? = { _, _ -> }
        ): List<Any?> {
            var result1: Any? = null
            var result2: Any? = null
            var start = 0
            var i = anchor

            orientationFuncCore({
                while (start - offset <= scRecycleView.measuredHeight && i < scRecycleView.adapter.getItemCount()) {
                    result1 = block1(i, start)
                    start += scRecycleView.recycler.scViewHolderList[i].itemView.measuredHeight
                    i += 1
                }
                result2 = block3(i, start)
                return@orientationFuncCore null
            },
                {
                    while (start - offset <= scRecycleView.measuredWidth && i < scRecycleView.adapter.getItemCount()) {
                        result1 = block2(i, start)
                        start += scRecycleView.recycler.scViewHolderList[i].itemView.measuredWidth
                        i += 1
                    }
                    result2 = block4(i, start)
                    return@orientationFuncCore null
                })
            return listOf(result1, result2)
        }

        private fun getEndDistance(): Float {
            return mainFuncCore(
                block3 = { _, start ->
                    return@mainFuncCore if (start - offset <= scRecycleView.measuredHeight)
                        start - offset - scRecycleView.measuredHeight else 0f
                },
                block4 = { _, start ->
                    return@mainFuncCore if (start - offset <= scRecycleView.measuredWidth)
                        start - offset - scRecycleView.measuredWidth else 0f
                })[1] as Float
        }

        private fun findClickedChild(x: Float, y: Float): View? {
            return mainFuncCore({ i, _ ->
                val viewHolder = scRecycleView.recycler.scViewHolderList[i]
                if (viewHolder.itemView.left < x && x < viewHolder.itemView.right &&
                    viewHolder.itemView.top < y && y < viewHolder.itemView.bottom
                ) {
                    return@mainFuncCore viewHolder.itemView
                }
                return@mainFuncCore null
            }, { i, _ ->
                val viewHolder = scRecycleView.recycler.scViewHolderList[i]
                if (viewHolder.itemView.left < x && x < viewHolder.itemView.right &&
                    viewHolder.itemView.top < y && y < viewHolder.itemView.bottom
                ) {
                    return@mainFuncCore viewHolder.itemView
                }
                return@mainFuncCore null
            })[0] as View?
        }

        private fun viewHolderBindingRecycler() {
            for (i in 0 until scRecycleView.adapter.getItemCount()) {
                val v = scRecycleView.adapter.onCreateViewHolder(scRecycleView, 0)
                scRecycleView.recycler.scViewHolderList.add(v)
                scRecycleView.addView(v.itemView)
            }
            scRecycleView.recycler.isInitial = true
        }

        private fun canChildScroll(x: Float, y: Float): Boolean {
            val view = findClickedChild(x, y)
            return orientationFuncCore({
                if (view != null) {
                    return@orientationFuncCore view.canScrollHorizontally(direction)
                }
                false
            },
                {
                    if (view != null) {
                        return@orientationFuncCore view.canScrollVertically(direction)
                    }
                    false
                }) as Boolean
        }

        private fun onScroll() {
            orientationFuncCore({
                while (anchor < scRecycleView.adapter.getItemCount() && offset > scRecycleView.recycler.scViewHolderList[anchor].itemView.measuredHeight) {
                    offset -= scRecycleView.recycler.scViewHolderList[anchor].itemView.measuredHeight
                    anchor += 1
                }
                offset += getEndDistance()
                while (offset < 0) {
                    if (anchor == 0) {
                        offset = 0f
                    } else {
                        anchor -= 1
                        offset += scRecycleView.recycler.scViewHolderList[anchor].itemView.measuredHeight
                    }
                }
            },
                {
                    while (anchor < scRecycleView.adapter.getItemCount() && offset > scRecycleView.recycler.scViewHolderList[anchor].itemView.measuredWidth) {
                        offset -= scRecycleView.recycler.scViewHolderList[anchor].itemView.measuredWidth
                        anchor += 1
                    }
                    offset += getEndDistance()
                    while (offset < 0) {
                        if (anchor == 0) {
                            offset = 0f
                        } else {
                            anchor -= 1
                            offset += scRecycleView.recycler.scViewHolderList[anchor].itemView.measuredWidth
                        }
                    }
                })
            scRecycleView.requestLayout()
        }

        private fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (orientationFuncCore({
                            distance = downPosition.second - ev.y
                            direction = (distance / distance.absoluteValue).toInt()
                            return@orientationFuncCore (cantScrollVertically(direction))
                        },
                            {
                                distance = downPosition.first - ev.x
                                direction = (distance / distance.absoluteValue).toInt()
                                return@orientationFuncCore (cantScrollHorizontally(direction))
                            }) as Boolean
                    ) {
                        return false
                    }

                    isScrolling = true
                    downPosition = Pair(ev.x, ev.y)
                    offset += distance
                    onScroll()
                }

                MotionEvent.ACTION_UP -> isScrolling = false
            }
            return true
        }

        fun scrollTo(length: Int) {
            if (!(orientationFuncCore({
                    direction = (length / length.absoluteValue)
                    return@orientationFuncCore (cantScrollVertically(direction))
                },
                    {
                        direction = (length / length.absoluteValue)
                        return@orientationFuncCore (cantScrollHorizontally(direction))
                    }) as Boolean) && this::scRecycleView.isInitialized
            ) {
                anchor = 0
                offset += length
                onScroll()
            }
        }

        fun scrollBy(length: Int) {
            if (!(orientationFuncCore({
                    direction = (length / length.absoluteValue)
                    return@orientationFuncCore (cantScrollVertically(direction))
                },
                    {
                        direction = (length / length.absoluteValue)
                        return@orientationFuncCore (cantScrollHorizontally(direction))
                    }) as Boolean) && this::scRecycleView.isInitialized
            ) {
                offset += length
                onScroll()
            }
        }

        fun scrollToPosition(position: Int) {
            if (!(orientationFuncCore({
                    direction = (position / position.absoluteValue)
                    return@orientationFuncCore (cantScrollVertically(direction))
                },
                    {
                        direction = (position / position.absoluteValue)
                        return@orientationFuncCore (cantScrollHorizontally(direction))
                    }) as Boolean) && this::scRecycleView.isInitialized
            ) {
                anchor = min(scRecycleView.adapter.getItemCount() - 1, max(0, position))
                offset = 0f
                onScroll()
            }
        }

        fun scrollByPosition(position: Int) {
            if (!(orientationFuncCore({
                    direction = (position / position.absoluteValue)
                    return@orientationFuncCore (cantScrollVertically(direction))
                },
                    {
                        direction = (position / position.absoluteValue)
                        return@orientationFuncCore (cantScrollHorizontally(direction))
                    }) as Boolean) && this::scRecycleView.isInitialized
            ) {
                anchor = min(scRecycleView.adapter.getItemCount() - 1, max(0, anchor + position))
                offset = 0f
                onScroll()
            }
        }

        fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int, view: SCRecycleView) {
            if (!this::scRecycleView.isInitialized) {
                scRecycleView = view
                scRecycleView.adapter.scRecycleView = view
            }
            if (!scRecycleView.recycler.isInitial) {
                scRecycleView.adapter.notifyDataSetChanged()
                scRecycleView.recycler.isInitial = true
            }
            mainFuncCore({ i, _ ->
                scRecycleView.adapter.onBindingViewHolder(
                    scRecycleView.recycler.scViewHolderList[i],
                    i
                )
                scRecycleView.measureChild(
                    scRecycleView.recycler.scViewHolderList[i].itemView,
                    widthMeasureSpec,
                    heightMeasureSpec
                )
            },
                { i, _ ->
                    scRecycleView.adapter.onBindingViewHolder(
                        scRecycleView.recycler.scViewHolderList[i],
                        i
                    )
                    scRecycleView.measureChild(
                        scRecycleView.recycler.scViewHolderList[i].itemView,
                        widthMeasureSpec,
                        heightMeasureSpec
                    )
                },
                { _, start ->
                    endFlag = start - offset <= scRecycleView.measuredHeight
                    return@mainFuncCore null
                },
                { _, start ->
                    endFlag = start - offset <= scRecycleView.measuredWidth
                    return@mainFuncCore null
                })
            scRecycleView.setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
        }

        fun onLayout(left: Int, top: Int, right: Int, bottom: Int) {
            mainFuncCore({ i, start ->
                val viewHolder = scRecycleView.recycler.scViewHolderList[i]
                viewHolder.itemView.layout(
                    0,
                    (start - offset).toInt(),
                    viewHolder.itemView.measuredWidth,
                    (start - offset + viewHolder.itemView.measuredHeight).toInt()
                )
            },
                { i, start ->
                    val viewHolder = scRecycleView.recycler.scViewHolderList[i]
                    viewHolder.itemView.layout(
                        (start - offset).toInt(),
                        0,
                        (start - offset + viewHolder.itemView.measuredWidth).toInt(),
                        viewHolder.itemView.measuredHeight
                    )
                })
        }

        fun cantScrollVertically(direction: Int): Boolean =
            orientationFuncCore({
                when (direction) {
                    -1 -> offset == 0f && anchor == 0 && !isScrolling
                    1 -> endFlag && !isScrolling
                    else -> false
                }
            }, { false }) as Boolean

        fun cantScrollHorizontally(direction: Int): Boolean =
            orientationFuncCore({ false }, {
                when (direction) {
                    -1 -> offset == 0f && anchor == 0 && !isScrolling
                    1 -> endFlag && !isScrolling
                    else -> false
                }
            }) as Boolean

        fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
            var onInterceptTouchEvent = false
            when (ev!!.action) {
                MotionEvent.ACTION_DOWN ->
                    downPosition = Pair(ev.x, ev.y)

                MotionEvent.ACTION_MOVE -> {
                    onInterceptTouchEvent = orientationFuncCore({
                        !(canChildScroll(
                            ev.x, ev.y
                        )) && !cantScrollHorizontally(direction)
                    },
                        {
                            !(canChildScroll(
                                ev.x, ev.y
                            )) && !cantScrollVertically(direction)
                        }) as Boolean
                }
            }
            if (!onInterceptTouchEvent) {
                findClickedChild(ev.x, ev.y)?.dispatchTouchEvent(ev)
            }

            return onTouchEvent(ev)
        }

        fun dispatchDraw(canvas: Canvas?) {
            mainFuncCore({ i, _ ->
                val viewHolder = scRecycleView.recycler.scViewHolderList[i]
                scRecycleView.drawChild(
                    canvas,
                    viewHolder.itemView,
                    viewHolder.itemView.drawingTime
                )
            },
                { i, _ ->
                    val viewHolder = scRecycleView.recycler.scViewHolderList[i]
                    scRecycleView.drawChild(
                        canvas,
                        viewHolder.itemView,
                        viewHolder.itemView.drawingTime
                    )
                })
        }
    }

    private val recycler = SCRecycler()
    lateinit var layoutManager: SCLayoutManager
    lateinit var adapter: SCAdapter<SCViewHolder>

    override fun onLayout(p0: Boolean, p1: Int, p2: Int, p3: Int, p4: Int) =
        layoutManager.onLayout(p1, p2, p3, p4)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        layoutManager.onMeasure(widthMeasureSpec, heightMeasureSpec, this)

    override fun dispatchDraw(canvas: Canvas?) {
        layoutManager.dispatchDraw(canvas)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?) =
        layoutManager.dispatchTouchEvent(ev)

    override fun canScrollVertically(direction: Int) =
        !layoutManager.cantScrollVertically(direction)

    override fun canScrollHorizontally(direction: Int) =
        !layoutManager.cantScrollHorizontally(direction)
}