package com.example.losnote.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.google.android.material.card.MaterialCardView;

/**
 * 能拦截横向滑动手势的卡片。
 *
 * 关键：重写 onInterceptTouchEvent —— 当手指明显横向移动时，
 * 拦截事件交给自己的 onTouchEvent（GestureDetector）处理，
 * 这样无论触摸落在哪个子 View（标题/空白/ScrollView）上，
 * 横滑切题都能生效；而纵向滑动不拦截，留给内部 ScrollView 滚动。
 */
public class SwipeCardView extends MaterialCardView {

    private float downX, downY;
    private static final int H_SLOP = 24;   // 横向触发阈值(px)

    public SwipeCardView(@NonNull Context context) {
        super(context);
    }

    public SwipeCardView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeCardView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);
                // 横向移动明显大于纵向 -> 拦截，交给本 View 的 onTouchEvent
                if (dx > dy && dx > H_SLOP) {
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }
}
