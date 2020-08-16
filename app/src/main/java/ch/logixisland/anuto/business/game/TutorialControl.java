package ch.logixisland.anuto.business.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import ch.logixisland.anuto.Preferences;
import ch.logixisland.anuto.R;
import ch.logixisland.anuto.business.tower.TowerInserter;
import ch.logixisland.anuto.business.tower.TowerSelector;
import ch.logixisland.anuto.business.wave.WaveManager;

public class TutorialControl implements TowerInserter.Listener, WaveManager.Listener, TowerSelector.Listener {

    public interface TutorialView {
        void showHint(int textId, boolean showSkipButton);

        void tutorialFinished();
    }

    private enum State {
        BuildTower,
        Credits,
        TowerOptions1,
        TowerOptions2,
        TowerOptions3,
        TowerOptions4,
        TowerOptions5,
        Enemies,
        Finish,
        Idle;

        public State next() {
            State[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    private final SharedPreferences mPreferences;

    private TutorialView mView;
    private State mState;

    public TutorialControl(Context context, TowerInserter towerInserter, TowerSelector towerSelector, WaveManager waveManager) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        towerInserter.addListener(this);
        waveManager.addListener(this);
        towerSelector.addListener(this);
    }

    public void restart() {
        mPreferences.edit()
                .putBoolean(Preferences.TUTORIAL_ENABLED, true)
                .apply();

        initialize();
    }

    public void setView(TutorialView view) {
        mView = view;

        if (mView != null) {
            initialize();
        }
    }

    public void gotItClicked() {
        mState = mState.next();
        activate();

        if (mState == State.Idle) {
            mPreferences.edit()
                    .putBoolean(Preferences.TUTORIAL_ENABLED, false)
                    .apply();
        }
    }

    public void skipClicked() {
        mState = State.Finish;
        activate();
    }

    @Override
    public void towerInserted() {
        if (mState == State.BuildTower) {
            mState = mState.next();
            activate();
        }
    }

    @Override
    public void towerInfoShown() {
        if (mState == State.TowerOptions1) {
            mState = mState.next();
            activate();
        }
    }

    @Override
    public void waveStarted() {
        if (mState == State.TowerOptions5) {
            mState = mState.next();
            activate();
        }
    }

    @Override
    public void waveNumberChanged() {

    }

    @Override
    public void autoWaveChanged() {

    }

    @Override
    public void nextWaveReadyChanged() {

    }

    @Override
    public void remainingEnemiesCountChanged() {

    }

    private void initialize() {
        if (mPreferences.getBoolean(Preferences.TUTORIAL_ENABLED, true)) {
            mState = State.BuildTower;
        } else {
            mState = State.Idle;
        }

        activate();
    }

    private void activate() {
        switch (mState) {
            case BuildTower:
                mView.showHint(R.string.tutorial_build_tower, true);
                break;
            case Credits:
                mView.showHint(R.string.tutorial_credits, false);
                break;
            case TowerOptions1:
                mView.showHint(R.string.tutorial_tower_options_1, false);
                break;
            case TowerOptions2:
                mView.showHint(R.string.tutorial_tower_options_2, false);
                break;
            case TowerOptions3:
                mView.showHint(R.string.tutorial_tower_options_3, false);
                break;
            case TowerOptions4:
                mView.showHint(R.string.tutorial_tower_options_4, false);
                break;
            case TowerOptions5:
                mView.showHint(R.string.tutorial_next_wave, false);
                break;
            case Enemies:
                mView.showHint(R.string.tutorial_enemies, false);
                break;
            case Finish:
                mView.showHint(R.string.tutorial_finish, false);
                break;
            default:
                mView.tutorialFinished();
                break;
        }
    }
}
