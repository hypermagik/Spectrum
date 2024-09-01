package com.hypermagik.spectrum.analyzer

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener

class GestureHandler(private val view: AnalyzerView) :
    OnScaleGestureListener, GestureDetector.OnGestureListener {

    override fun onScale(p0: ScaleGestureDetector): Boolean {
        view.onScale(p0.scaleFactor, p0.focusX, p0.focusY)
        return true
    }

    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(p0: ScaleGestureDetector) {}

    override fun onDown(p0: MotionEvent): Boolean {
        return true
    }

    override fun onShowPress(p0: MotionEvent) {}

    override fun onSingleTapUp(p0: MotionEvent): Boolean {
        return true
    }

    override fun onScroll(p0: MotionEvent?, p1: MotionEvent, p2: Float, p3: Float): Boolean {
        view.onScroll(p2)
        return true
    }

    override fun onLongPress(p0: MotionEvent) {
        return
    }

    override fun onFling(p0: MotionEvent?, p1: MotionEvent, p2: Float, p3: Float): Boolean {
        return true
    }
}