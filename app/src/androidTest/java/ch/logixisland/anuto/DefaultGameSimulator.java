package ch.logixisland.anuto;

import java.util.List;
import java.util.Random;

import ch.logixisland.anuto.business.game.ScoreBoard;
import ch.logixisland.anuto.business.tower.TowerControl;
import ch.logixisland.anuto.business.tower.TowerInserter;
import ch.logixisland.anuto.business.tower.TowerSelector;
import ch.logixisland.anuto.business.wave.WaveManager;
import ch.logixisland.anuto.engine.logic.GameEngine;
import ch.logixisland.anuto.engine.logic.loop.TickTimer;
import ch.logixisland.anuto.engine.logic.map.MapPath;
import ch.logixisland.anuto.entity.EntityTypes;
import ch.logixisland.anuto.entity.plateau.Plateau;
import ch.logixisland.anuto.entity.tower.Tower;
import ch.logixisland.anuto.entity.tower.TowerStrategy;
import ch.logixisland.anuto.util.iterator.Function;
import ch.logixisland.anuto.util.iterator.Predicate;
import ch.logixisland.anuto.util.iterator.StreamIterator;
import ch.logixisland.anuto.util.math.Intersections;
import ch.logixisland.anuto.util.math.Line;
import ch.logixisland.anuto.util.math.Vector2;
import ch.logixisland.anuto.view.game.GameActivity;

public class DefaultGameSimulator extends GameSimulator {

    private static final int MAX_TIER = 3;
    private static final int MAX_UNENHANCED_TOWERS_PER_TIER = 2;
    private static final int MAX_ENHANCED_TOWERS_PER_TIER = 4;
    private static final int NO_SLOW_DOWN_TOWERS_BEFORE_WAVE = 20;

    private final TowerTiers mTowerTiers;
    private final Random mRandom = new Random();
    private final TickTimer mSaveAndLoadTimer = TickTimer.createInterval(120f);
    private final TickTimer mSGTimer = TickTimer.createInterval(60f);
    private final TickTimer mAutoWaveTickTimer = TickTimer.createInterval(20f);
    private final TickTimer mSimulationTickTimer = TickTimer.createInterval(0.5f);

    private int mSGMode = 0;

    DefaultGameSimulator(GameActivity gameActivity, GameFactory gameFactory) {
        super(gameActivity, gameFactory);
        mTowerTiers = new TowerTiers(gameFactory);
    }

    @Override
    protected void tick() {
        if (mSimulationTickTimer.tick()) {
            tryUpgradeTower();
            tryEnhanceTower();
            tryBuildTower();
            tryStartNextWave();
        }

        if (mSaveAndLoadTimer.tick()) {
            saveAndLoad();
        }

        if (mSGTimer.tick()) {
            switch (mSGMode = (mSGMode + 1) % 4) {
                case 1:
                    saveSG();
                    break;
                default:
                case 2:
                    //for mSaveAndLoadTimer
                    break;
                case 3:
                    loadSG();
                    break;
                case 4:
                    deleteSG();
                    break;
            }
        }

        if (mAutoWaveTickTimer.tick()) {
            switchAutoWave();
        }
    }

    private void tryUpgradeTower() {
        final ScoreBoard scoreBoard = getGameFactory().getScoreBoard();
        final TowerSelector towerSelector = getGameFactory().getTowerSelector();
        final TowerControl towerControl = getGameFactory().getTowerControl();

        StreamIterator<Tower> iterator = getTowers();
        while (iterator.hasNext()) {
            Tower tower = iterator.next();
            final int tier = mTowerTiers.getTowerTier(tower);

            if (!tower.isUpgradeable() || tower.getUpgradeCost() > scoreBoard.getCredits()) {
                continue;
            }

            if (tower.getLevel() > 1) {
                continue;
            }

            int unenhancedTowersInNextTier = getTowers()
                    .filter(new Predicate<Tower>() {
                        @Override
                        public boolean apply(Tower tower) {
                            return mTowerTiers.getTowerTier(tower) == tier + 1 && tower.getLevel() == 1;
                        }
                    })
                    .count();

            if (unenhancedTowersInNextTier >= MAX_UNENHANCED_TOWERS_PER_TIER && tier + 1 != MAX_TIER) {
                continue;
            }

            int notAffordableUpgradesInSameTier = getTowers()
                    .filter(new Predicate<Tower>() {
                        @Override
                        public boolean apply(Tower tower) {
                            return mTowerTiers.getTowerTier(tower) == tier && tower.isUpgradeable() && tower.getUpgradeCost() > scoreBoard.getCredits();
                        }
                    })
                    .count();

            if (notAffordableUpgradesInSameTier > 0) {
                continue;
            }

            Vector2 position = tower.getPosition();

            towerSelector.selectTower(tower);
            towerControl.upgradeTower();

            randomizeTowerStrategy(position);
        }
    }

    private void tryEnhanceTower() {
        final TowerSelector towerSelector = getGameFactory().getTowerSelector();
        final TowerControl towerControl = getGameFactory().getTowerControl();
        final ScoreBoard scoreBoard = getGameFactory().getScoreBoard();

        StreamIterator<Tower> iterator = getTowers();
        while (iterator.hasNext()) {
            Tower tower = iterator.next();
            final int tier = mTowerTiers.getTowerTier(tower);

            if (!tower.isEnhanceable() || tower.getEnhanceCost() > scoreBoard.getCredits()) {
                continue;
            }

            int enhancedTowersInTier = getTowers()
                    .filter(new Predicate<Tower>() {
                        @Override
                        public boolean apply(Tower tower) {
                            return mTowerTiers.getTowerTier(tower) == tier && tower.getLevel() > 1;
                        }
                    })
                    .count();

            if (enhancedTowersInTier >= MAX_ENHANCED_TOWERS_PER_TIER) {
                continue;
            }

            towerSelector.selectTower(tower);
            towerControl.enhanceTower();
            return;
        }
    }

    private void tryBuildTower() {
        final TowerInserter towerInserter = getGameFactory().getTowerInserter();
        final ScoreBoard scoreBoard = getGameFactory().getScoreBoard();
        final WaveManager waveManager = getGameFactory().getWaveManager();

        int unenhancedTowerCount = getTowers()
                .filter(new Predicate<Tower>() {
                    @Override
                    public boolean apply(Tower tower) {
                        return mTowerTiers.getTowerTier(tower) == 1 && tower.getLevel() == 1;
                    }
                })
                .count();

        if (unenhancedTowerCount >= MAX_UNENHANCED_TOWERS_PER_TIER) {
            return;
        }

        int unaffordableTowers = mTowerTiers.getBuildableTowers()
                .filter(new Predicate<Tower>() {
                    @Override
                    public boolean apply(Tower tower) {
                        return tower.getValue() > scoreBoard.getCredits();
                    }
                })
                .count();

        if (unaffordableTowers > 0) {
            return;
        }

        Tower selectedTower = mTowerTiers.getBuildableTowers()
                .filter(new Predicate<Tower>() {
                    @Override
                    public boolean apply(Tower tower) {
                        return tower.getDamage() > 0 || waveManager.getWaveNumber() > NO_SLOW_DOWN_TOWERS_BEFORE_WAVE;
                    }
                })
                .random(mRandom);

        if (selectedTower == null) {
            return;
        }

        Plateau selectedPlateau = findTowerPlateau(selectedTower);

        if (selectedPlateau == null) {
            return;
        }

        towerInserter.insertTower(selectedTower.getEntityName());
        towerInserter.setPosition(selectedPlateau.getPosition());
        towerInserter.buyTower();

        randomizeTowerStrategy(selectedPlateau.getPosition());
    }

    private void randomizeTowerStrategy(Vector2 position) {
        final TowerSelector towerSelector = getGameFactory().getTowerSelector();
        final TowerControl towerControl = getGameFactory().getTowerControl();

        boolean toggleLock = mRandom.nextBoolean();
        int cycleStrategyCount = mRandom.nextInt(TowerStrategy.values().length);

        towerSelector.selectTowerAt(position);

        if (toggleLock) {
            towerControl.toggleLockTarget();
        }

        for (int i = 0; i < cycleStrategyCount; i++) {
            towerControl.cycleTowerStrategy();
        }
    }

    private Plateau findTowerPlateau(Tower tower) {
        final List<MapPath> paths = getGameFactory().getGameEngine().getGameMap().getPaths();
        final float range = tower.getRange();

        final Function<Plateau, Float> distanceCovered = new Function<Plateau, Float>() {
            @Override
            public Float apply(Plateau plateau) {
                float distanceCovered = 0;

                for (MapPath path : paths) {
                    for (Line line : Intersections.getPathSectionsInRange(path.getWayPoints(), plateau.getPosition(), range)) {
                        distanceCovered += line.length();
                    }
                }

                return distanceCovered;
            }
        };

        final Float maxDistanceCovered = getFreePlateaus()
                .map(distanceCovered)
                .max(new Function<Float, Float>() {
                    @Override
                    public Float apply(Float input) {
                        return input;
                    }
                });

        if (maxDistanceCovered == null) {
            return null;
        }

        return getFreePlateaus()
                .filter(new Predicate<Plateau>() {
                    @Override
                    public boolean apply(Plateau value) {
                        return distanceCovered.apply(value) > maxDistanceCovered * 0.8f;
                    }
                })
                .random(mRandom);
    }

    private void tryStartNextWave() {
        final WaveManager waveManager = getGameFactory().getWaveManager();

        if (waveManager.isNextWaveReady() && waveManager.getRemainingEnemiesCount() < 50) {
            waveManager.startNextWave();
        }
    }

    private void switchAutoWave() {
        final WaveManager waveManager = getGameFactory().getWaveManager();

        boolean newState = !waveManager.isAutoNextWaveActive();
        if (newState && waveManager.getRemainingEnemiesCount() < 60) {
            waveManager.setAutoNextWaveActive(true);
        } else
            waveManager.setAutoNextWaveActive(false);
    }

    private StreamIterator<Tower> getTowers() {
        final GameEngine gameEngine = getGameFactory().getGameEngine();

        return gameEngine.getEntitiesByType(EntityTypes.TOWER)
                .cast(Tower.class);
    }

    private StreamIterator<Plateau> getFreePlateaus() {
        final GameEngine gameEngine = getGameFactory().getGameEngine();

        return gameEngine.getEntitiesByType(EntityTypes.PLATEAU)
                .cast(Plateau.class)
                .filter(Plateau.unoccupied());
    }

}
