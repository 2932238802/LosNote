package com.example.losnote.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/**
 * 用于 Markdown 宽表格/长代码的横向滚动容器。
 * 触摸落在内容区时，主动禁止父容器拦截事件，避免被卡片左右切题手势抢走。
 */
public class NestedHorizontalScrollView extends HorizontalScrollView {

    public NestedHorizontalScrollView(Context context) {
        super(context);
    }

    public NestedHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NestedHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
}
