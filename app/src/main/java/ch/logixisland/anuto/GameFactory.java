package ch.logixisland.anuto;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import ch.logixisland.anuto.business.game.GameLoader;
import ch.logixisland.anuto.business.game.GameSpeed;
import ch.logixisland.anuto.business.game.GameState;
import ch.logixisland.anuto.business.game.HighScores;
import ch.logixisland.anuto.business.game.MapRepository;
import ch.logixisland.anuto.business.game.ScoreBoard;
import ch.logixisland.anuto.business.game.TutorialControl;
import ch.logixisland.anuto.business.tower.TowerAging;
import ch.logixisland.anuto.business.tower.TowerControl;
import ch.logixisland.anuto.business.tower.TowerInserter;
import ch.logixisland.anuto.business.tower.TowerSelector;
import ch.logixisland.anuto.business.wave.WaveManager;
import ch.logixisland.anuto.engine.logic.GameEngine;
import ch.logixisland.anuto.engine.logic.entity.EntityRegistry;
import ch.logixisland.anuto.engine.logic.entity.EntityStore;
import ch.logixisland.anuto.engine.logic.loop.FrameRateLogger;
import ch.logixisland.anuto.engine.logic.loop.GameLoop;
import ch.logixisland.anuto.engine.logic.loop.MessageQueue;
import ch.logixisland.anuto.engine.logic.persistence.GamePersister;
import ch.logixisland.anuto.engine.render.Renderer;
import ch.logixisland.anuto.engine.render.Viewport;
import ch.logixisland.anuto.engine.render.sprite.SpriteFactory;
import ch.logixisland.anuto.engine.sound.SoundFactory;
import ch.logixisland.anuto.engine.sound.SoundManager;
import ch.logixisland.anuto.engine.theme.ThemeManager;
import ch.logixisland.anuto.entity.enemy.Blob;
import ch.logixisland.anuto.entity.enemy.Flyer;
import ch.logixisland.anuto.entity.enemy.Healer;
import ch.logixisland.anuto.entity.enemy.Soldier;
import ch.logixisland.anuto.entity.enemy.Sprinter;
import ch.logixisland.anuto.entity.plateau.BasicPlateau;
import ch.logixisland.anuto.entity.tower.BouncingLaser;
import ch.logixisland.anuto.entity.tower.Canon;
import ch.logixisland.anuto.entity.tower.DualCanon;
import ch.logixisland.anuto.entity.tower.GlueGun;
import ch.logixisland.anuto.entity.tower.GlueTower;
import ch.logixisland.anuto.entity.tower.MachineGun;
import ch.logixisland.anuto.entity.tower.MineLayer;
import ch.logixisland.anuto.entity.tower.Mortar;
import ch.logixisland.anuto.entity.tower.RocketLauncher;
import ch.logixisland.anuto.entity.tower.SimpleLaser;
import ch.logixisland.anuto.entity.tower.StraightLaser;
import ch.logixisland.anuto.entity.tower.Teleporter;

public class GameFactory {

    // Other
    private final SharedPreferences mPreferences;

    // Engine
    private ThemeManager mThemeManager;
    private SoundManager mSoundManager;
    private SpriteFactory mSpriteFactory;
    private SoundFactory mSoundFactory;
    private Viewport mViewport;
    private FrameRateLogger mFrameRateLogger;
    private EntityStore mEntityStore;
    private MessageQueue mMessageQueue;
    private Renderer mRenderer;
    private GameEngine mGameEngine;
    private GameLoop mGameLoop;
    private GamePersister mGamePersister;
    private EntityRegistry mEntityRegistry;

    // Business
    private ScoreBoard mScoreBoard;
    private HighScores mHighScores;
    private TowerSelector mTowerSelector;
    private TowerControl mTowerControl;
    private TowerAging mTowerAging;
    private TowerInserter mTowerInserter;
    private MapRepository mMapRepository;
    private GameLoader mGameLoader;
    private WaveManager mWaveManager;
    private GameSpeed mSpeedManager;
    private GameState mGameState;
    private TutorialControl mTutorialControl;

    public GameFactory(Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.settings, false);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        initializeEngine(context);
        registerEntities();
        initializeBusiness(context);
        registerPersisters();
    }

    private void initializeEngine(Context context) {
        mViewport = new Viewport();
        mEntityStore = new EntityStore();
        mMessageQueue = new MessageQueue();
        mGamePersister = new GamePersister();
        mFrameRateLogger = new FrameRateLogger();
        mRenderer = new Renderer(mViewport, mFrameRateLogger);
        mGameLoop = new GameLoop(mRenderer, mFrameRateLogger, mMessageQueue, mEntityStore);
        mThemeManager = new ThemeManager(context, mRenderer);
        mSoundManager = new SoundManager(context);
        mSpriteFactory = new SpriteFactory(context, mThemeManager);
        mSoundFactory = new SoundFactory(context, mSoundManager);
        mGameEngine = new GameEngine(mSpriteFactory, mThemeManager, mSoundFactory, mEntityStore, mMessageQueue, mRenderer, mGameLoop);
        mEntityRegistry = new EntityRegistry(mGameEngine);
    }

    private void registerEntities() {
        mEntityRegistry.registerEntity(BasicPlateau.ENTITY_NAME, new BasicPlateau.Factory(), new BasicPlateau.Persister());

        mEntityRegistry.registerEntity(Blob.ENTITY_NAME, new Blob.Factory(), new Blob.Persister());
        mEntityRegistry.registerEntity(Flyer.ENTITY_NAME, new Flyer.Factory(), new Flyer.Persister());
        mEntityRegistry.registerEntity(Healer.ENTITY_NAME, new Healer.Factory(), new Healer.Persister());
        mEntityRegistry.registerEntity(Soldier.ENTITY_NAME, new Soldier.Factory(), new Soldier.Persister());
        mEntityRegistry.registerEntity(Sprinter.ENTITY_NAME, new Sprinter.Factory(), new Sprinter.Persister());

        mEntityRegistry.registerEntity(Canon.ENTITY_NAME, new Canon.Factory(), new Canon.Persister());
        mEntityRegistry.registerEntity(DualCanon.ENTITY_NAME, new DualCanon.Factory(), new DualCanon.Persister());
        mEntityRegistry.registerEntity(MachineGun.ENTITY_NAME, new MachineGun.Factory(), new MachineGun.Persister());
        mEntityRegistry.registerEntity(SimpleLaser.ENTITY_NAME, new SimpleLaser.Factory(), new SimpleLaser.Persister());
        mEntityRegistry.registerEntity(BouncingLaser.ENTITY_NAME, new BouncingLaser.Factory(), new BouncingLaser.Persister());
        mEntityRegistry.registerEntity(StraightLaser.ENTITY_NAME, new StraightLaser.Factory(), new StraightLaser.Persister());
        mEntityRegistry.registerEntity(Mortar.ENTITY_NAME, new Mortar.Factory(), new Mortar.Persister());
        mEntityRegistry.registerEntity(MineLayer.ENTITY_NAME, new MineLayer.Factory(), new MineLayer.Persister());
        mEntityRegistry.registerEntity(RocketLauncher.ENTITY_NAME, new RocketLauncher.Factory(), new RocketLauncher.Persister());
        mEntityRegistry.registerEntity(GlueTower.ENTITY_NAME, new GlueTower.Factory(), new GlueTower.Persister());
        mEntityRegistry.registerEntity(GlueGun.ENTITY_NAME, new GlueGun.Factory(), new GlueGun.Persister());
        mEntityRegistry.registerEntity(Teleporter.ENTITY_NAME, new Teleporter.Factory(), new Teleporter.Persister());
    }

    private void initializeBusiness(Context context) {
        mMapRepository = new MapRepository();
        mScoreBoard = new ScoreBoard(mGameEngine);
        mTowerSelector = new TowerSelector(mGameEngine, mScoreBoard);
        mGameLoader = new GameLoader(context, mGameEngine, mGamePersister, mViewport, mEntityRegistry, mMapRepository, mRenderer);
        mHighScores = new HighScores(context, mGameEngine, mScoreBoard, mGameLoader);
        mGameState = new GameState(mScoreBoard, mHighScores, mTowerSelector);
        mTowerAging = new TowerAging(mGameEngine);
        mSpeedManager = new GameSpeed(mGameEngine);
        mWaveManager = new WaveManager(mGameEngine, mScoreBoard, mGameState, mEntityRegistry, mTowerAging);
        mTowerControl = new TowerControl(mGameEngine, mScoreBoard, mTowerSelector, mEntityRegistry);
        mTowerInserter = new TowerInserter(mGameEngine, mGameState, mEntityRegistry, mTowerSelector, mTowerAging, mScoreBoard, mPreferences);
        mTutorialControl = new TutorialControl(context, mTowerInserter, mTowerSelector, mWaveManager);
    }

    private void registerPersisters() {
        mGamePersister.registerPersister(mMessageQueue);
        mGamePersister.registerPersister(mScoreBoard);
        mGamePersister.registerPersister(mGameState);
        mGamePersister.registerPersister(mEntityRegistry);
        mGamePersister.registerPersister(mWaveManager);
    }

    public ThemeManager getThemeManager() {
        return mThemeManager;
    }

    public Viewport getViewport() {
        return mViewport;
    }

    public Renderer getRenderer() {
        return mRenderer;
    }

    public GameEngine getGameEngine() {
        return mGameEngine;
    }

    public EntityRegistry getEntityRegistry() {
        return mEntityRegistry;
    }

    public ScoreBoard getScoreBoard() {
        return mScoreBoard;
    }

    public TowerSelector getTowerSelector() {
        return mTowerSelector;
    }

    public TowerControl getTowerControl() {
        return mTowerControl;
    }

    public TowerInserter getTowerInserter() {
        return mTowerInserter;
    }

    public GameLoader getGameLoader() {
        return mGameLoader;
    }

    public WaveManager getWaveManager() {
        return mWaveManager;
    }

    public GameSpeed getSpeedManager() {
        return mSpeedManager;
    }

    public GameState getGameState() {
        return mGameState;
    }

    public MapRepository getMapRepository() {
        return mMapRepository;
    }

    public HighScores getHighScores() {
        return mHighScores;
    }

    public TutorialControl getTutorialControl() {
        return mTutorialControl;
    }

}
