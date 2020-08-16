package ch.logixisland.anuto.business.wave;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.logixisland.anuto.GameSettings;
import ch.logixisland.anuto.Preferences;
import ch.logixisland.anuto.business.game.GameState;
import ch.logixisland.anuto.business.game.ScoreBoard;
import ch.logixisland.anuto.business.tower.TowerAging;
import ch.logixisland.anuto.engine.logic.GameEngine;
import ch.logixisland.anuto.engine.logic.entity.EntityRegistry;
import ch.logixisland.anuto.engine.logic.loop.Message;
import ch.logixisland.anuto.engine.logic.map.MapPath;
import ch.logixisland.anuto.engine.logic.map.WaveInfo;
import ch.logixisland.anuto.engine.logic.persistence.Persister;
import ch.logixisland.anuto.util.container.KeyValueStore;

public class WaveManager implements Persister, GameState.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = WaveManager.class.getSimpleName();

    private static final float NEXT_WAVE_MIN_DELAY = 5;

    private final SharedPreferences mSharedPreferences;
    private boolean mAutoWavesEnabled;
    private boolean mAutoWavesActive;

    public interface Listener {
        void waveStarted();

        void waveNumberChanged();

        void nextWaveReadyChanged();

        void autoWaveChanged();

        void remainingEnemiesCountChanged();
    }

    private final GameEngine mGameEngine;
    private final ScoreBoard mScoreBoard;
    private final GameState mGameState;
    private final TowerAging mTowerAging;
    private final EntityRegistry mEntityRegistry;
    private final EnemyDefaultHealth mEnemyDefaultHealth;

    private int mWaveNumber;
    private int mRemainingEnemiesCount;
    private boolean mNextWaveReady;

    private final List<WaveAttender> mActiveWaves = new ArrayList<>();
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();

    public WaveManager(GameEngine gameEngine, ScoreBoard scoreBoard, GameState gameState,
                       EntityRegistry entityRegistry, TowerAging towerAging, SharedPreferences sharedPreferences) {
        mGameEngine = gameEngine;
        mScoreBoard = scoreBoard;
        mGameState = gameState;
        mTowerAging = towerAging;
        mEntityRegistry = entityRegistry;

        mEnemyDefaultHealth = new EnemyDefaultHealth(entityRegistry);

        mGameState.addListener(this);

        mSharedPreferences = sharedPreferences;
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mAutoWavesEnabled = mSharedPreferences.getBoolean(Preferences.AUTO_WAVES_ENABLED, false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Preferences.AUTO_WAVES_ENABLED.equals(key)) {
            mAutoWavesEnabled = mSharedPreferences.getBoolean(Preferences.AUTO_WAVES_ENABLED, false);
            handleAutoWave();
        }
    }

    public int getWaveNumber() {
        return mWaveNumber;
    }

    public boolean isNextWaveReady() {
        return mNextWaveReady;
    }

    public int getRemainingEnemiesCount() {
        return mRemainingEnemiesCount;
    }

    public void startNextWave() {
        if (mGameEngine.isThreadChangeNeeded()) {
            mGameEngine.post(new Message() {
                @Override
                public void execute() {
                    startNextWave();
                }
            });
            return;
        }

        if (!mNextWaveReady) {
            return;
        }

        setNextWaveReady(false);
        nextWaveReadyDelayed(NEXT_WAVE_MIN_DELAY);

        mGameState.gameStarted();

        giveWaveRewardAndEarlyBonus();
        createAndStartWaveAttender();
        updateBonusOnScoreBoard();
        updateRemainingEnemiesCount();

        setWaveNumber(mWaveNumber + 1);

        for (Listener listener : mListeners) {
            listener.waveStarted();
        }
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void resetState() {
        setWaveNumber(0);
        mActiveWaves.clear();
        setNextWaveReady(true);
    }

    @Override
    public void writeState(KeyValueStore gameState) {
        gameState.putInt("waveNumber", mWaveNumber);

        for (WaveAttender waveAttender : mActiveWaves) {
            gameState.appendStore("activeWaves", waveAttender.writeActiveWaveData());
        }
    }

    @Override
    public void readState(KeyValueStore gameState) {
        initializeActiveWaves(gameState);
        initializeNextWaveReady();
        setWaveNumber(gameState.getInt("waveNumber"));
        updateRemainingEnemiesCount();
    }

    @Override
    public void gameRestart() {

    }

    @Override
    public void gameOver() {
        setNextWaveReady(false);
    }

    private void initializeActiveWaves(KeyValueStore gameState) {
        mActiveWaves.clear();

        for (KeyValueStore activeWaveData : gameState.getStoreList("activeWaves")) {
            List<WaveInfo> waveInfos = mGameEngine.getWaveInfos();
            WaveInfo waveInfo = waveInfos.get(activeWaveData.getInt("waveNumber") % waveInfos.size());
            List<MapPath> paths = mGameEngine.getGameMap().getPaths();
            WaveAttender waveAttender = new WaveAttender(mGameEngine, mScoreBoard, mEntityRegistry, this, waveInfo, paths, activeWaveData.getInt("waveNumber"));
            waveAttender.readActiveWaveData(activeWaveData);
            waveAttender.start();
            mActiveWaves.add(waveAttender);
        }
    }

    private void initializeNextWaveReady() {
        if (mGameState.isGameOver()) {
            setNextWaveReady(false);
            return;
        }

        int minWaveDelayTicks = Math.round(NEXT_WAVE_MIN_DELAY * GameEngine.TARGET_FRAME_RATE);
        int lastStartedWaveTickCount = -minWaveDelayTicks;

        for (WaveAttender wave : mActiveWaves) {
            lastStartedWaveTickCount = Math.max(lastStartedWaveTickCount, wave.getWaveStartTickCount());
        }

        int nextWaveReadyTicks = minWaveDelayTicks - (mGameEngine.getTickCount() - lastStartedWaveTickCount);
        float nextWaveReadyDelay = (float) nextWaveReadyTicks / GameEngine.TARGET_FRAME_RATE;

        if (nextWaveReadyDelay > 0f) {
            setNextWaveReady(false);
            nextWaveReadyDelayed(nextWaveReadyDelay);
        } else {
            setNextWaveReady(true);
        }
    }

    void enemyRemoved() {
        updateBonusOnScoreBoard();
        updateRemainingEnemiesCount();
    }

    void waveFinished(WaveAttender waveAttender) {
        mActiveWaves.remove(waveAttender);

        mTowerAging.ageTowers();
        updateBonusOnScoreBoard();
    }

    private void giveWaveRewardAndEarlyBonus() {
        WaveAttender currentWave = getCurrentWave();

        if (currentWave != null) {
            currentWave.giveWaveReward();
            mScoreBoard.giveCredits(getEarlyBonus(), false);
        }
    }

    private void nextWaveReadyDelayed(float delay) {
        mGameEngine.postDelayed(new Message() {
            @Override
            public void execute() {
                if (!mGameState.isGameOver()) {
                    setNextWaveReady(true);
                }
            }
        }, delay);
    }

    private void updateBonusOnScoreBoard() {
        mScoreBoard.setEarlyBonus(getEarlyBonus());

        WaveAttender currentWave = getCurrentWave();
        if (currentWave != null) {
            mScoreBoard.setWaveBonus(currentWave.getWaveReward());
        } else {
            mScoreBoard.setWaveBonus(0);
        }
    }

    private void updateRemainingEnemiesCount() {
        int totalCount = 0;

        for (WaveAttender waveAttender : mActiveWaves) {
            totalCount += waveAttender.getRemainingEnemiesCount();
        }

        if (mRemainingEnemiesCount != totalCount) {
            mRemainingEnemiesCount = totalCount;

            //nur wenn (mRemainingEnemiesCount < 200) aktiv ist
            //autoWave();

            for (Listener listener : mListeners) {
                listener.remainingEnemiesCountChanged();
            }
        }
    }

    private void createAndStartWaveAttender() {
        List<WaveInfo> waveInfos = mGameEngine.getWaveInfos();
        WaveInfo nextWaveInfo = waveInfos.get(mWaveNumber % waveInfos.size());
        List<MapPath> paths = mGameEngine.getGameMap().getPaths();
        WaveAttender nextWave = new WaveAttender(mGameEngine, mScoreBoard, mEntityRegistry, this, nextWaveInfo, paths, mWaveNumber);
        updateWaveExtend(nextWave, nextWaveInfo);
        updateWaveModifiers(nextWave);
        nextWave.start();
        mActiveWaves.add(nextWave);
    }

    private void updateWaveExtend(WaveAttender wave, WaveInfo waveInfo) {
        int extend = Math.min((getIterationNumber() - 1) * waveInfo.getExtend(), waveInfo.getMaxExtend());
        wave.setExtend(extend);
    }

    private void updateWaveModifiers(WaveAttender wave) {
        float waveHealth = wave.getWaveDefaultHealth(this.mEnemyDefaultHealth);
        float damagePossible = GameSettings.DIFFICULTY_LINEAR * mScoreBoard.getCreditsEarned()
                + GameSettings.DIFFICULTY_MODIFIER * (float) Math.pow(mScoreBoard.getCreditsEarned(), GameSettings.DIFFICULTY_EXPONENT);
        float healthModifier = damagePossible / waveHealth;
        healthModifier = Math.max(healthModifier, GameSettings.MIN_HEALTH_MODIFIER);

        float rewardModifier = GameSettings.REWARD_MODIFIER * (float) Math.pow(healthModifier, GameSettings.REWARD_EXPONENT);
        rewardModifier = Math.max(rewardModifier, GameSettings.MIN_REWARD_MODIFIER);

        wave.modifyEnemyHealth(healthModifier);
        wave.modifyEnemyReward(rewardModifier);
        wave.modifyWaveReward(getIterationNumber());

        Log.i(TAG, String.format("waveNumber=%d", getWaveNumber()));
        Log.i(TAG, String.format("waveHealth=%f", waveHealth));
        Log.i(TAG, String.format("creditsEarned=%d", mScoreBoard.getCreditsEarned()));
        Log.i(TAG, String.format("damagePossible=%f", damagePossible));
        Log.i(TAG, String.format("healthModifier=%f", healthModifier));
        Log.i(TAG, String.format("rewardModifier=%f", rewardModifier));
    }

    private int getIterationNumber() {
        return (getWaveNumber() / mGameEngine.getWaveInfos().size()) + 1;
    }

    private int getEarlyBonus() {
        float remainingReward = 0;

        for (WaveAttender wave : mActiveWaves) {
            remainingReward += wave.getRemainingEnemiesReward();
        }

        return Math.round(GameSettings.EARLY_BONUS_MODIFIER * (float) Math.pow(remainingReward, GameSettings.EARLY_BONUS_EXPONENT));
    }

    private WaveAttender getCurrentWave() {
        if (mActiveWaves.isEmpty()) {
            return null;
        }

        return mActiveWaves.get(mActiveWaves.size() - 1);
    }

    private void setWaveNumber(int waveNumber) {
        if (mWaveNumber != waveNumber) {
            mWaveNumber = waveNumber;

            for (Listener listener : mListeners) {
                listener.waveNumberChanged();
            }
        }
    }

    private void setNextWaveReady(boolean ready) {
        if (mNextWaveReady != ready) {
            mNextWaveReady = ready;

            if (handleAutoWave()) {
                return;
            }

            for (Listener listener : mListeners) {
                listener.nextWaveReadyChanged();
            }
        }
    }

    public boolean isAutoNextWaveActive() {
        return mAutoWavesActive;
    }

    public void setAutoNextWaveActive(boolean checked) {
        mAutoWavesActive = checked;
        handleAutoWave();

        for (Listener listener : mListeners) {
            listener.autoWaveChanged();
        }
    }

    private boolean handleAutoWave() {
        if (mAutoWavesEnabled && mAutoWavesActive && (mWaveNumber > 0) && (mNextWaveReady) && (true || (mRemainingEnemiesCount < 200))) {
            startNextWave();
            return true;
        }

        return false;
    }
}
