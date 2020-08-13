package ch.logixisland.anuto.entity.tower;

import android.graphics.Canvas;
import android.graphics.Paint;

import ch.logixisland.anuto.AnutoApplication;
import ch.logixisland.anuto.R;
import ch.logixisland.anuto.business.game.ScoreBoard;
import ch.logixisland.anuto.engine.render.Drawable;
import ch.logixisland.anuto.engine.render.Layers;
import ch.logixisland.anuto.engine.theme.Theme;
import ch.logixisland.anuto.util.math.Vector2;

public class LevelIndicator implements Drawable {

    private final Theme mTheme;
    private final Tower mTower;
    private final Paint mText;
    private final Paint mTextBorder;
    private final Paint mStratText;
    private final Paint mStratTextBorder;
    private final ScoreBoard mScoreBoard;


    LevelIndicator(Theme theme, Tower tower) {
        mTheme = theme;
        mTower = tower;
        mScoreBoard = AnutoApplication.getInstance().getGameFactory().getScoreBoard();

        mText = new Paint();
        mText.setStyle(Paint.Style.FILL);
        mText.setColor(mTheme.getColor(R.attr.levelIndicatorColorLvl1));
        mText.setTextSize(60);
        mTextBorder = new Paint(mText);
        mTextBorder.setStyle(Paint.Style.STROKE);
        mTextBorder.setColor(mTheme.getColor(R.attr.levelIndicatorColorStroke));
        mTextBorder.setStrokeWidth(4);

        mStratText = new Paint(mText);
        mStratText.setTextSize(30);
        mStratTextBorder = new Paint(mTextBorder);
        mStratTextBorder.setTextSize(30);
    }

    @Override
    public void draw(Canvas canvas) {
        Vector2 pos = mTower.getPosition();

        canvas.save();
        canvas.translate(pos.x(), pos.y());
        canvas.scale(0.0075f, -0.0075f);
        switch (mTower.getUpgradeLevel()) {
            case 2: {
                mText.setColor(mTheme.getColor(R.attr.levelIndicatorColorLvl2));
                mStratText.setColor(mTheme.getColor(R.attr.levelIndicatorColorLvl2));
            }
            break;
            case 3: {
                mText.setColor(mTheme.getColor(R.attr.levelIndicatorColorLvl3));
                mStratText.setColor(mTheme.getColor(R.attr.levelIndicatorColorLvl3));
            }
            break;
        }

        Aimer aimer = mTower.getAimer();
        String strategy = null;

        if (aimer != null) {
            strategy = String.valueOf(aimer.getStrategy().name().charAt(0)).toUpperCase();
            if (aimer.doesLockTarget())
                strategy += "+";
        }

        int level = mTower.getLevel();
        int credits = mScoreBoard.getCredits();

        String text = "";
        if (level < mTower.getMaxLevel()) {
            text = String.valueOf(level);

            if (mTower.isEnhanceable() && (mTower.getEnhanceCost() <= credits))
                text += "*";
        }
        if (mTower.isUpgradeable() && (mTower.getUpgradeCost() <= credits))
            text = "^" + text;

        float height = mText.ascent() + mText.descent();
        float width = mText.measureText(text);
        canvas.drawText(text, -width / 2, -height / 2, mTextBorder);
        canvas.drawText(text, -width / 2, -height / 2, mText);

        if (strategy != null) {
            //height = mStratText.ascent() + mStratText.descent();
            width = mStratText.measureText(strategy, 0, 1);
            canvas.drawText(strategy, -width / 2, height + mStratText.descent(), mStratTextBorder);
            canvas.drawText(strategy, -width / 2, height + mStratText.descent(), mStratText);
        }

        canvas.restore();
    }

    @Override
    public int getLayer() {
        return Layers.TOWER_LEVEL;
    }
}
