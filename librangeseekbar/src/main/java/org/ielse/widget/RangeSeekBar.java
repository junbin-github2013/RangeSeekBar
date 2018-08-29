package org.ielse.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import ielse.org.widget.R;

public class RangeSeekBar extends View {

    private float DENSITY;
    private Paint mPaint = new Paint();

    // line
    private int mLineTop, mLineBottom, mLineLeft, mLineRight;
    private int mLineCorners;
    private int mLineWidth, mLineHeight;
    private RectF mLine = new RectF();

    private int mLineShadeColorStart;
    private int mLineShadeColorEnd;
    private int mLineEdgeColor;

    // left right seek bar
    private SeekBar mLeftSeekBar = new SeekBar();
    private SeekBar mRightSeekBar = new SeekBar();
    private SeekBar mTouchSeekBar;
    private int mLeftSeekBarResId;
    private int mRightSeekBarResId;
    private int mTipsResId;

    // scale mark
    private float mMaxValue, mMinValue, mOffsetValue;
    private int mCellsCount = 1;
    private float mCellsPercent;
    private float mReserveValue;
    private int mReserveCount;
    private float mReservePercent;
    private boolean mMoving;

    // listener
    private OnRangeChangedListener mChangeListener;

    public RangeSeekBar(Context context) {
        this(context, null);
    }

    public RangeSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray t = context.obtainStyledAttributes(attrs, R.styleable.RangeSeekBar);
        DENSITY = Resources.getSystem().getDisplayMetrics().density;
        mLineHeight = t.getDimensionPixelOffset(R.styleable.RangeSeekBar_lineHeight, (int) (DENSITY * 5));
        mLeftSeekBarResId = t.getResourceId(R.styleable.RangeSeekBar_leftSeekBarResId, 0);
        mRightSeekBarResId = t.getResourceId(R.styleable.RangeSeekBar_rightSeekBarResId, 0);
        mTipsResId = t.getResourceId(R.styleable.RangeSeekBar_tipsResId, 0);
        mLineShadeColorStart = t.getColor(R.styleable.RangeSeekBar_lineColorShadeStart, 0xFF00B4FF);
        mLineShadeColorEnd = t.getColor(R.styleable.RangeSeekBar_lineColorShadeEnd, 0xFF0E83FF);
        mLineEdgeColor = t.getColor(R.styleable.RangeSeekBar_lineColorEdge, 0xFFD7D7D7);
        float min = t.getFloat(R.styleable.RangeSeekBar_min, 0);
        float max = t.getFloat(R.styleable.RangeSeekBar_max, 1);
        float reserve = t.getFloat(R.styleable.RangeSeekBar_reserve, 0);
        int cells = t.getInt(R.styleable.RangeSeekBar_cells, 1);
        setRules(min, max, reserve, cells);
        t.recycle();
    }

    public void setOnRangeChangedListener(OnRangeChangedListener listener) {
        mChangeListener = listener;
    }

    public void setValue(float min, float max) {
        min = min + mOffsetValue;
        max = max + mOffsetValue;

        if (min < mMinValue) {
            throw new IllegalArgumentException("setValue() min < (preset min - offsetValue) . #min:" + min
                    + " #preset min:" + mMinValue + " #offsetValue:" + mOffsetValue);
        }
        if (max > mMaxValue) {
            throw new IllegalArgumentException("setValue() max > (preset max - offsetValue) . #max:" + max
                    + " #preset max:" + mMaxValue + " #offsetValue:" + mOffsetValue);
        }

        if (mReserveCount > 1) {
            if ((min - mMinValue) % mReserveCount != 0) {
                throw new IllegalArgumentException("setValue() (min - preset min) % reserveCount != 0 . #min:"
                        + min + " #preset min:" + mMinValue + "#reserveCount:" + mReserveCount + "#reserve:" + mReserveValue);
            }
            if ((max - mMinValue) % mReserveCount != 0) {
                throw new IllegalArgumentException("setValue() (max - preset min) % reserveCount != 0 . #max:"
                        + max + " #preset min:" + mMinValue + "#reserveCount:" + mReserveCount + "#reserve:" + mReserveValue);
            }
            mLeftSeekBar.mCurrPercent = (min - mMinValue) / mReserveCount * mCellsPercent;
            mRightSeekBar.mCurrPercent = (max - mMinValue) / mReserveCount * mCellsPercent;
        } else {
            mLeftSeekBar.mCurrPercent = (min - mMinValue) / (mMaxValue - mMinValue);
            mRightSeekBar.mCurrPercent = (max - mMinValue) / (mMaxValue - mMinValue);
        }
        invalidate();
    }

    public void setRules(float min, float max) {
        setRules(min, max, mReserveCount, mCellsCount);
    }

    public void setRules(float min, float max, float reserve, int cells) {
        if (max <= min) {
            throw new IllegalArgumentException("setRules() max must be greater than min ! #max:" + max + " #min:" + min);
        }
        if (min < 0) {
            mOffsetValue = 0 - min;
            min = min + mOffsetValue;
            max = max + mOffsetValue;
        }
        mMinValue = min;
        mMaxValue = max;

        if (reserve < 0) {
            throw new IllegalArgumentException("setRules() reserve must be greater than zero ! #reserve:" + reserve);
        }
        if (reserve >= max - min) {
            throw new IllegalArgumentException("setRules() reserve must be less than (max - min) ! #reserve:"
                    + reserve + " #max - min:" + (max - min));
        }
        if (cells < 1) {
            throw new IllegalArgumentException("setRules() cells must be greater than 1 ! #cells:" + cells);
        }
        mCellsCount = cells;
        mCellsPercent = 1f / mCellsCount;
        mReserveValue = reserve;
        mReservePercent = reserve / (max - min);
        mReserveCount = (int) (mReservePercent / mCellsPercent + (mReservePercent % mCellsPercent != 0 ? 1 : 0));
        if (mCellsCount > 1) {
            if (mLeftSeekBar.mCurrPercent + mCellsPercent * mReserveCount <= 1
                    && mLeftSeekBar.mCurrPercent + mCellsPercent * mReserveCount > mRightSeekBar.mCurrPercent) {
                mRightSeekBar.mCurrPercent = mLeftSeekBar.mCurrPercent + mCellsPercent * mReserveCount;
            } else if (mRightSeekBar.mCurrPercent - mCellsPercent * mReserveCount >= 0
                    && mRightSeekBar.mCurrPercent - mCellsPercent * mReserveCount < mLeftSeekBar.mCurrPercent) {
                mLeftSeekBar.mCurrPercent = mRightSeekBar.mCurrPercent - mCellsPercent * mReserveCount;
            }
        } else {
            if (mLeftSeekBar.mCurrPercent + mReservePercent <= 1
                    && mLeftSeekBar.mCurrPercent + mReservePercent > mRightSeekBar.mCurrPercent) {
                mRightSeekBar.mCurrPercent = mLeftSeekBar.mCurrPercent + mReservePercent;
            } else if (mRightSeekBar.mCurrPercent - mReservePercent >= 0
                    && mRightSeekBar.mCurrPercent - mReservePercent < mLeftSeekBar.mCurrPercent) {
                mLeftSeekBar.mCurrPercent = mRightSeekBar.mCurrPercent - mReservePercent;
            }
        }
        invalidate();
    }

    public float[] getCurrentRange() {
        float range = mMaxValue - mMinValue;
        return new float[]{-mOffsetValue + mMinValue + range * mLeftSeekBar.mCurrPercent,
                -mOffsetValue + mMinValue + range * mRightSeekBar.mCurrPercent};
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightSize * 1.8f > widthSize) {
            setMeasuredDimension(widthSize, (int) (widthSize / 1.8f));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int baseLineLeft = 80;
        int baseLineBottom = h * 2 / 3;

        mLineLeft = baseLineLeft;
        mLineRight = w - baseLineLeft;
        mLineWidth = mLineRight - mLineLeft;

        mLineTop = baseLineBottom - mLineHeight / 2;
        mLineBottom = baseLineBottom + mLineHeight / 2;

        mLine.set(mLineLeft, mLineTop, mLineRight, mLineBottom);
        mLineCorners = (int) ((mLineBottom - mLineTop) * 0.45f);

        mLeftSeekBar.onSizeChanged(baseLineLeft, baseLineBottom, h, mLineWidth, mCellsCount > 1,
                mLeftSeekBarResId, mTipsResId, getContext());
        mRightSeekBar.onSizeChanged(baseLineLeft, baseLineBottom, h, mLineWidth, mCellsCount > 1,
                mRightSeekBarResId, mTipsResId, getContext());

        if (mCellsCount == 1) {
            mRightSeekBar.mLeft += mLeftSeekBar.mWidth;
            mRightSeekBar.mRight += mLeftSeekBar.mWidth;
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mLineEdgeColor);
        mPaint.setShader(null);
        if (mCellsPercent > 0) {
            mPaint.setStrokeWidth(mLineCorners * 0.3f);
            for (int i = 1; i < mCellsCount; i++) {
                canvas.drawLine(mLineLeft + i * mCellsPercent * mLineWidth, mLineTop - mLineCorners,
                        mLineLeft + i * mCellsPercent * mLineWidth, mLineBottom + mLineCorners, mPaint);
            }
            if (mCellsCount == 1) {
                int midDividerLineHeight = (int) (DENSITY * 13);
                int x = mLineLeft + mLineWidth / 2;
                canvas.drawLine(x, mLineTop - midDividerLineHeight, x, mLineTop, mPaint);
            }
        }
        canvas.drawRoundRect(mLine, mLineCorners, mLineCorners, mPaint);
        drawRect(canvas);
        mLeftSeekBar.draw(canvas);
        mRightSeekBar.draw(canvas);
    }

    private void drawRect(Canvas canvas) {
        float left = mLeftSeekBar.mLeft + mLeftSeekBar.mWidth / 2 + mLeftSeekBar.mLineWidth * mLeftSeekBar.mCurrPercent;
        float right = mRightSeekBar.mLeft + mRightSeekBar.mWidth / 2 + mRightSeekBar.mLineWidth * mRightSeekBar.mCurrPercent;
        float top = mMoving ? mLineTop - mLineHeight / 2 : mLineTop;
        float bottom = mMoving ? mLineBottom + mLineHeight / 2 : mLineBottom;
        mPaint.setShader(new LinearGradient(left, 0, right, 0,
                mLineShadeColorStart, mLineShadeColorEnd, Shader.TileMode.CLAMP));
        canvas.drawRect(left, top, right, bottom, mPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            // down
            case MotionEvent.ACTION_DOWN:
                boolean touchResult = false;
                if (mRightSeekBar.mCurrPercent >= 1 && mLeftSeekBar.collide(event)) {
                    mTouchSeekBar = mLeftSeekBar;
                    touchResult = true;
                    mMoving = true;
                } else if (mRightSeekBar.collide(event)) {
                    mTouchSeekBar = mRightSeekBar;
                    touchResult = true;
                    mMoving = true;
                } else if (mLeftSeekBar.collide(event)) {
                    mTouchSeekBar = mLeftSeekBar;
                    touchResult = true;
                    mMoving = true;
                }
                return touchResult;

            // move
            case MotionEvent.ACTION_MOVE:
                float percent;
                float x = event.getX();

                mTouchSeekBar.mMaterial = mTouchSeekBar.mMaterial >= 1 ? 1 : mTouchSeekBar.mMaterial + 0.1f;

                if (mTouchSeekBar == mLeftSeekBar) {
                    if (mCellsCount > 1) {
                        if (x < mLineLeft) {
                            percent = 0;
                        } else {
                            percent = (x - mLineLeft) * 1f / (mLineWidth);
                        }
                        int touchLeftCellsValue = Math.round(percent / mCellsPercent);
                        int currRightCellsValue = Math.round(mRightSeekBar.mCurrPercent / mCellsPercent);
                        percent = touchLeftCellsValue * mCellsPercent;

                        while (touchLeftCellsValue > currRightCellsValue - mReserveCount) {
                            touchLeftCellsValue--;
                            if (touchLeftCellsValue < 0) {
                                break;
                            }
                            percent = touchLeftCellsValue * mCellsPercent;
                        }
                    } else {
                        if (x < mLineLeft) {
                            percent = 0;
                        } else {
                            percent = (x - mLineLeft) * 1f / (mLineWidth - mRightSeekBar.mWidth);
                        }

                        if (percent > mRightSeekBar.mCurrPercent - mReservePercent) {
                            percent = mRightSeekBar.mCurrPercent - mReservePercent;
                        }
                    }
                    mLeftSeekBar.slide(percent);
                    mMoving = true;
                } else if (mTouchSeekBar == mRightSeekBar) {
                    if (mCellsCount > 1) {
                        if (x > mLineRight) {
                            percent = 1;
                        } else {
                            percent = (x - mLineLeft) * 1f / (mLineWidth);
                        }
                        int touchRightCellsValue = Math.round(percent / mCellsPercent);
                        int currLeftCellsValue = Math.round(mLeftSeekBar.mCurrPercent / mCellsPercent);
                        percent = touchRightCellsValue * mCellsPercent;

                        while (touchRightCellsValue < currLeftCellsValue + mReserveCount) {
                            touchRightCellsValue++;
                            if (touchRightCellsValue > mMaxValue - mMinValue) {
                                break;
                            }
                            percent = touchRightCellsValue * mCellsPercent;
                        }
                    } else {
                        if (x > mLineRight) {
                            percent = 1;
                        } else {
                            percent = (x - mLineLeft - mLeftSeekBar.mWidth) * 1f / (mLineWidth - mLeftSeekBar.mWidth);
                        }
                        if (percent < mLeftSeekBar.mCurrPercent + mReservePercent) {
                            percent = mLeftSeekBar.mCurrPercent + mReservePercent;
                        }
                    }
                    mRightSeekBar.slide(percent);
                    mMoving = true;
                }

                if (mChangeListener != null) {
                    float[] result = getCurrentRange();
                    mChangeListener.onRangeChanged(this, result[0], result[1]);
                }
                invalidate();
                break;

            // up
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mMoving = false;
                mTouchSeekBar.materialRestore();

                if (mChangeListener != null) {
                    float[] result = getCurrentRange();
                    mChangeListener.onRangeChanged(this, result[0], result[1]);
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.minValue = mMinValue - mOffsetValue;
        ss.maxValue = mMaxValue - mOffsetValue;
        ss.reserveValue = mReserveValue;
        ss.cellsCount = mCellsCount;
        float[] results = getCurrentRange();
        ss.currSelectedMin = results[0];
        ss.currSelectedMax = results[1];
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        float min = ss.minValue;
        float max = ss.maxValue;
        float reserve = ss.reserveValue;
        int cells = ss.cellsCount;
        setRules(min, max, reserve, cells);
        float currSelectedMin = ss.currSelectedMin;
        float currSelectedMax = ss.currSelectedMax;
        setValue(currSelectedMin, currSelectedMax);
    }

    private class SavedState extends BaseSavedState {
        private float minValue;
        private float maxValue;
        private float reserveValue;
        private int cellsCount;
        private float currSelectedMin;
        private float currSelectedMax;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            minValue = in.readFloat();
            maxValue = in.readFloat();
            reserveValue = in.readFloat();
            cellsCount = in.readInt();
            currSelectedMin = in.readFloat();
            currSelectedMax = in.readFloat();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(minValue);
            out.writeFloat(maxValue);
            out.writeFloat(reserveValue);
            out.writeInt(cellsCount);
            out.writeFloat(currSelectedMin);
            out.writeFloat(currSelectedMax);
        }
    }

    /**
     * SeekBar
     */
    private class SeekBar {

        Paint mDefaultPaint;
        int mWidth, mHeight, mLineWidth;
        float mCurrPercent;
        int mLeft, mRight, mTop, mBottom, mTipTop;
        RadialGradient mShaderBar;
        Bitmap mBitmapBar;
        Bitmap mBitmapTips;

        float mMaterial = 0;
        ValueAnimator mAnim;
        final TypeEvaluator<Integer> mTypeEvaluator = new TypeEvaluator<Integer>() {
            @Override
            public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
                int alpha = (int) (Color.alpha(startValue) + fraction * (Color.alpha(endValue) - Color.alpha(startValue)));
                int red = (int) (Color.red(startValue) + fraction * (Color.red(endValue) - Color.red(startValue)));
                int green = (int) (Color.green(startValue) + fraction * (Color.green(endValue) - Color.green(startValue)));
                int blue = (int) (Color.blue(startValue) + fraction * (Color.blue(endValue) - Color.blue(startValue)));
                return Color.argb(alpha, red, green, blue);
            }
        };

        void onSizeChanged(int centerX, int centerY, int parentHeight, int parentLineWidth, boolean cellsMode,
                int barBmpResId, int tipsBmpResId, Context context) {
            mHeight = centerY;
            if (barBmpResId > 0) {
                mBitmapBar = BitmapFactory.decodeResource(context.getResources(), barBmpResId);
                mWidth = mBitmapBar.getWidth();
            } else {
                mWidth = (int) (mHeight * 0.8f);
                int radius = (int) (mWidth * 0.5f);
                int barShadowRadius = (int) (radius * 0.95f);
                int mShadowCenterX = mWidth / 2;
                int mShadowCenterY = mHeight / 2;
                mShaderBar = new RadialGradient(mShadowCenterX, mShadowCenterY, barShadowRadius,
                        Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            }
            mDefaultPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (tipsBmpResId > 0) {
                mBitmapTips = BitmapFactory.decodeResource(context.getResources(), tipsBmpResId);
                mTipTop = mHeight - mBitmapTips.getHeight();
            }

            mLeft = centerX - mWidth / 2;
            mRight = centerX + mWidth / 2;
            mTop = mHeight;
            mBottom = parentHeight;

            if (cellsMode) {
                mLineWidth = parentLineWidth;
            } else {
                mLineWidth = parentLineWidth - mWidth;
            }
        }

        boolean collide(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            int offset = (int) (mLineWidth * mCurrPercent);
            return x > mLeft + offset && x < mRight + offset && y > mTop && y < mBottom;
        }

        void slide(float percent) {
            if (percent < 0) {
                percent = 0;
            } else if (percent > 1) {
                percent = 1;
            }
            mCurrPercent = percent;
        }

        void draw(Canvas canvas) {
            int offset = (int) (mLineWidth * mCurrPercent);
            canvas.save();
            canvas.translate(offset, 0);
            if (mBitmapBar != null) {
                canvas.drawBitmap(mBitmapBar, mLeft, mTop - mBitmapBar.getHeight() / 2, null);
            } else {
                canvas.translate(mLeft, 0);
                drawShaderBar(canvas);
            }
            if (mMoving && mBitmapTips != null) {
                drawTips(canvas);
            }
            canvas.restore();
        }

        private void drawShaderBar(Canvas canvas) {
            int centerX = mWidth / 2;
            int radius = (int) (mWidth * 0.5f);
            // draw shadow
            mDefaultPaint.setStyle(Paint.Style.FILL);
            canvas.save();
            canvas.translate(0, radius * 0.25f);
            canvas.scale(1 + (0.1f * mMaterial), 1 + (0.1f * mMaterial), centerX, mTop);
            mDefaultPaint.setShader(mShaderBar);
            canvas.drawCircle(centerX, mTop, radius, mDefaultPaint);
            mDefaultPaint.setShader(null);
            canvas.restore();
            // draw body
            mDefaultPaint.setStyle(Paint.Style.FILL);
            mDefaultPaint.setColor(mTypeEvaluator.evaluate(mMaterial, 0xFFFFFFFF, 0xFFE7E7E7));
            canvas.drawCircle(centerX, mTop, radius, mDefaultPaint);
            // draw border
            mDefaultPaint.setStyle(Paint.Style.STROKE);
            mDefaultPaint.setColor(0xFFD7D7D7);
            canvas.drawCircle(centerX, mTop, radius, mDefaultPaint);
        }

        private void drawTips(Canvas canvas) {
            canvas.drawBitmap(mBitmapTips, mLeft, mTipTop - mBitmapTips.getHeight(), null);
            mDefaultPaint.setColor(Color.WHITE);
            mDefaultPaint.setTextSize(24);
            mDefaultPaint.setFakeBoldText(true);

            float result = mMinValue + (mMaxValue - mMinValue) * mCurrPercent - mOffsetValue;
            String text = ((int) result) + "万";
            TextPaint textPaint = new TextPaint();
            textPaint.setTextSize(24);
            int textX = (int) (mLeft + mBitmapTips.getWidth() / 2 - textPaint.measureText(text) / 2);
            canvas.drawText(text, textX, mTipTop - 24, mDefaultPaint);
        }

        private void materialRestore() {
            if (mAnim != null) {
                mAnim.cancel();
            }
            mAnim = ValueAnimator.ofFloat(mMaterial, 0);
            mAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mMaterial = (float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            mAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mMaterial = 0;
                    invalidate();
                }
            });
            mAnim.start();
        }
    }

    /**
     * OnRangeChangedListener
     */
    public interface OnRangeChangedListener {
        /**
         * range changed
         * @param view
         * @param min
         * @param max
         */
        void onRangeChanged(RangeSeekBar view, float min, float max);
    }
}
