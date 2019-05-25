package com.iflytek.cyber.iot.show.core.view.viewpage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.util.AttributeSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.iflytek.cyber.iot.show.core.utils.DensityUtil;


/**
 * 边框容器（显示内容）
 * Created by lzj on 2018/6/17.
 */
public class OutlineContainer extends FrameLayout implements Animatable {

    // 动画持续时间
    private static final long ANIMATION_DURATION = 500;
    // 延迟时间
    private static final long FRAME_DURATION = 1000 / 60;
    private static final int colorBlue = Color.BLUE;
    // 动画插补器
    private final Interpolator mInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t + 1.0f;
        }
    };
    // 画笔
    private Paint mOutlinePaint;
    //　设置是否执行
    private boolean mIsRunning = false;
    // 设置动画时间
    private long mStartTime;
    // 透明度
    private float mAlpha = 1.0f;
    //　开启Runnable线程
    private final Runnable mUpdater = new Runnable() {
        @Override
        public void run() {
            long now = AnimationUtils.currentAnimationTimeMillis();
            long duration = now - mStartTime;
            if (duration >= ANIMATION_DURATION) {
                mAlpha = 0.0f;
                invalidate();
                stop();
                return;
            } else {
                mAlpha = mInterpolator.getInterpolation(1 - duration / (float) ANIMATION_DURATION);

                // 刷新界面
                invalidate();
            }
            postDelayed(mUpdater, FRAME_DURATION);
        }
    };
    private Context mContext;

    /**
     * 构造函数
     *
     * @param context
     */
    public OutlineContainer(Context context) {
        super(context);

        init(context);
    }

    public OutlineContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OutlineContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    // 初始化
    private void init(Context context) {
        mContext = context;
        mOutlinePaint = new Paint();
        mOutlinePaint.setAntiAlias(true);
        mOutlinePaint.setStrokeWidth(DensityUtil.dip2px(mContext, 2));
        mOutlinePaint.setColor(colorBlue);
        mOutlinePaint.setStyle(Paint.Style.STROKE);

        int padding = DensityUtil.dip2px(mContext, 1);
        setPadding(padding, padding, padding, padding);
    }

    // 组件进行绘制
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int offset = DensityUtil.dip2px(mContext, 5);
        if (mOutlinePaint.getColor() != JazzyViewPager.sOutlineColor) {
            mOutlinePaint.setColor(JazzyViewPager.sOutlineColor);
        }
        mOutlinePaint.setAlpha((int) (mAlpha * 255));
        Rect rect = new Rect(offset, offset, getMeasuredWidth() - offset, getMeasuredHeight() - offset);
        canvas.drawRect(rect, mOutlinePaint);
    }

    // 设置边框透明度
    public void setOutlineAlpha(float alpha) {
        mAlpha = alpha;
    }

    // 是否执行
    @Override
    public boolean isRunning() {
        return mIsRunning;
    }

    //　动画开始
    @Override
    public void start() {
        if (mIsRunning)
            return;
        mIsRunning = true;
        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        post(mUpdater);
    }

    //　动画停止
    @Override
    public void stop() {
        if (!mIsRunning)
            return;
        mIsRunning = false;
    }

}
