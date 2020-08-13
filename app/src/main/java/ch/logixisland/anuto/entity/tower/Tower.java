package ch.logixisland.anuto.entity.tower;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.preference.PreferenceManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.logixisland.anuto.AnutoApplication;
import ch.logixisland.anuto.Preferences;
import ch.logixisland.anuto.engine.logic.GameEngine;
import ch.logixisland.anuto.engine.logic.entity.Entity;
import ch.logixisland.anuto.engine.logic.loop.TickTimer;
import ch.logixisland.anuto.entity.EntityTypes;
import ch.logixisland.anuto.entity.enemy.Enemy;
import ch.logixisland.anuto.entity.enemy.WeaponType;
import ch.logixisland.anuto.entity.plateau.Plateau;
import ch.logixisland.anuto.util.iterator.StreamIterator;

public abstract class Tower extends Entity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final SharedPreferences mPreferences;
    private boolean mShowLevelEnabled;

    public interface Listener {
        void damageInflicted(float totalDamage);

        void valueChanged(int value);

        void strengthChanged();
    }

    private TowerProperties mTowerProperties;

    private boolean mBuilt;
    private int mValue;
    private int mLevel;
    private float mDamage;
    private float mRange;
    private float mReloadTime;
    private float mDamageInflicted;
    private boolean mReloaded = false;

    private Plateau mPlateau;

    private TickTimer mReloadTimer;
    private RangeIndicator mRangeIndicator;
    private LevelIndicator mLevelIndicator;

    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();

    Tower(GameEngine gameEngine, TowerProperties towerProperties) {
        super(gameEngine);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(AnutoApplication.getContext());

        mTowerProperties = towerProperties;

        mValue = mTowerProperties.getValue();
        mDamage = mTowerProperties.getDamage();
        mRange = mTowerProperties.getRange();
        mReloadTime = mTowerProperties.getReload();
        mLevel = 1;

        mReloadTimer = TickTimer.createInterval(mReloadTime);

        mBuilt = false;
    }

    @Override
    public final int getEntityType() {
        return EntityTypes.TOWER;
    }

    @Override
    public void clean() {
        super.clean();
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
        hideRange();
        hideLevel();

        if (mPlateau != null) {
            mPlateau.setOccupied(false);
            mPlateau = null;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (mBuilt && !mReloaded && mReloadTimer.tick()) {
            mReloaded = true;
        }
    }

    public Aimer getAimer() {
        return null;
    }

    public abstract void preview(Canvas canvas);

    public abstract List<TowerInfoValue> getTowerInfoValues();

    public Plateau getPlateau() {
        return mPlateau;
    }

    public void setPlateau(Plateau plateau) {
        if (plateau.isOccupied()) {
            throw new RuntimeException("Plateau already occupied!");
        }

        mPlateau = plateau;
        mPlateau.setOccupied(true);
        setPosition(mPlateau.getPosition());
    }

    public boolean isBuilt() {
        return mBuilt;
    }

    public void setBuilt() {
        mBuilt = true;
        mReloaded = true;

        mPreferences.registerOnSharedPreferenceChangeListener(this);
        updateStrength();
        updateShowLevel();
    }

    public WeaponType getWeaponType() {
        return mTowerProperties.getWeaponType();
    }

    public boolean isReloaded() {
        return mReloaded;
    }

    public void setReloaded(boolean reloaded) {
        mReloaded = reloaded;
    }

    public int getValue() {
        return mValue;
    }

    public void setValue(int value) {
        mValue = value;

        for (Listener listener : mListeners) {
            listener.valueChanged(mValue);
        }
    }

    public float getDamage() {
        return mDamage;
    }

    public float getRange() {
        return mRange;
    }

    public float getReloadTime() {
        return mReloadTime;
    }

    public float getDamageInflicted() {
        return mDamageInflicted;
    }

    public void reportDamageInflicted(float amount) {
        mDamageInflicted += amount;

        for (Listener listener : mListeners) {
            listener.damageInflicted(mDamageInflicted);
        }
    }

    void setDamageInflicted(float damageInflicted) {
        mDamageInflicted = damageInflicted;
    }

    public boolean isUpgradeable() {
        return mTowerProperties.getUpgradeTowerName() != null;
    }

    public String getUpgradeName() {
        return mTowerProperties.getUpgradeTowerName();
    }

    public int getUpgradeCost() {
        return mTowerProperties.getUpgradeCost();
    }

    public int getUpgradeLevel() {
        return mTowerProperties.getUpgradeLevel();
    }

    private void updateStrength() {

        for (Listener listener : mListeners) {
            listener.strengthChanged();
        }
    }

    public void enhance() {
        mValue += getEnhanceCost();
        mDamage += mTowerProperties.getEnhanceDamage() * (float) Math.pow(mTowerProperties.getEnhanceBase(), mLevel - 1);
        mRange += mTowerProperties.getEnhanceRange();
        mReloadTime -= mTowerProperties.getEnhanceReload();

        mLevel++;

        mReloadTimer.setInterval(mReloadTime);
        updateStrength();
    }

    public boolean isEnhanceable() {
        return mLevel < mTowerProperties.getMaxLevel();
    }

    public int getEnhanceCost() {
        if (!isEnhanceable()) {
            return -1;
        }

        return Math.round(mTowerProperties.getEnhanceCost() * (float) Math.pow(mTowerProperties.getEnhanceBase(), mLevel - 1));
    }

    public int getLevel() {
        return mLevel;
    }

    public int getMaxLevel() {
        return mTowerProperties.getMaxLevel();
    }

    public void showRange() {
        if (mRangeIndicator == null) {
            mRangeIndicator = new RangeIndicator(getTheme(), this);
            getGameEngine().add(mRangeIndicator);
        }
    }

    public void hideRange() {
        if (mRangeIndicator != null) {
            getGameEngine().remove(mRangeIndicator);
            mRangeIndicator = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Preferences.SHOW_TOWER_LEVELS_ENABLED.equals(key)) {
            updateShowLevel();
        }
    }

    private void updateShowLevel() {
        mShowLevelEnabled = mPreferences.getBoolean(Preferences.SHOW_TOWER_LEVELS_ENABLED, false);

        if (mShowLevelEnabled) {
            showLevel();
        } else {
            hideLevel();
        }
    }

    public void showLevel() {
        if (mLevelIndicator == null) {
            mLevelIndicator = new LevelIndicator(getTheme(), this);
            getGameEngine().add(mLevelIndicator);
        }
    }

    public void hideLevel() {
        if (mLevelIndicator != null) {
            getGameEngine().remove(mLevelIndicator);
            mLevelIndicator = null;
        }
    }

    public StreamIterator<Enemy> getPossibleTargets() {
        return getGameEngine().getEntitiesByType(EntityTypes.ENEMY)
                .filter(inRange(getPosition(), getRange()))
                .cast(Enemy.class);
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

}
