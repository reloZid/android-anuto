package ch.logixisland.anuto.entity.enemy;

import ch.logixisland.anuto.R;
import ch.logixisland.anuto.engine.logic.GameEngine;
import ch.logixisland.anuto.engine.logic.entity.Entity;
import ch.logixisland.anuto.engine.logic.entity.EntityFactory;
import ch.logixisland.anuto.engine.logic.entity.EntityRegistry;
import ch.logixisland.anuto.engine.logic.loop.TickListener;
import ch.logixisland.anuto.engine.render.Layers;
import ch.logixisland.anuto.engine.render.sprite.AnimatedSprite;
import ch.logixisland.anuto.engine.render.sprite.ReplicatedSprite;
import ch.logixisland.anuto.engine.render.sprite.SpriteInstance;
import ch.logixisland.anuto.engine.render.sprite.SpriteTemplate;
import ch.logixisland.anuto.engine.render.sprite.SpriteTransformation;
import ch.logixisland.anuto.engine.render.sprite.SpriteTransformer;
import ch.logixisland.anuto.util.math.Function;
import ch.logixisland.anuto.util.math.SampledFunction;

public class Sprinter extends Enemy implements SpriteTransformation {

    private final static String ENTITY_NAME = "sprinter";
    private final static float ANIMATION_SPEED = 0.7f;

    private final static EnemySettings ENEMY_SETTINGS = new EnemySettings(
            200,
            3.0f,
            0.03f,
            15,
            new WeaponType[]{WeaponType.Explosive},
            new WeaponType[]{WeaponType.Laser},
            2.0f,
            0.5f
    );

    public static class Factory extends EntityFactory {
        @Override
        public String getEntityName() {
            return ENTITY_NAME;
        }

        @Override
        public Entity create(GameEngine gameEngine) {
            return new Sprinter(gameEngine);
        }
    }

    public static class Persister extends EnemyPersister {
        public Persister(GameEngine gameEngine, EntityRegistry entityRegistry) {
            super(gameEngine, entityRegistry, ENTITY_NAME);
        }
    }

    private static class StaticData implements TickListener {
        SampledFunction mSpeedFunction;

        SpriteTemplate mSpriteTemplate;
        AnimatedSprite mReferenceSprite;

        @Override
        public void tick() {
            mReferenceSprite.tick();
            mSpeedFunction.step();
        }
    }

    private float mAngle;
    private StaticData mStatic;
    private ReplicatedSprite mSprite;

    private Sprinter(GameEngine gameEngine) {
        super(gameEngine, ENEMY_SETTINGS);
        mStatic = (StaticData) getStaticData();

        mSprite = getSpriteFactory().createReplication(mStatic.mReferenceSprite);
        mSprite.setListener(this);
    }

    @Override
    public String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    public Object initStatic() {
        StaticData s = new StaticData();

        s.mSpeedFunction = Function.sine()
                .multiply(0.9f)
                .offset(0.1f)
                .repeat((float) Math.PI)
                .stretch(GameEngine.TARGET_FRAME_RATE / ANIMATION_SPEED / (float) Math.PI)
                .sample();

        s.mSpriteTemplate = getSpriteFactory().createTemplate(R.attr.sprinter, 6);
        s.mSpriteTemplate.setMatrix(0.9f, 0.9f, null, null);

        s.mReferenceSprite = getSpriteFactory().createAnimated(Layers.ENEMY, s.mSpriteTemplate);
        s.mReferenceSprite.setSequenceForwardBackward();
        s.mReferenceSprite.setFrequency(ANIMATION_SPEED);

        getGameEngine().add(s);

        return s;
    }

    @Override
    public void init() {
        super.init();

        getGameEngine().add(mSprite);
    }

    @Override
    public void draw(SpriteInstance sprite, SpriteTransformer transformer) {
        transformer.translate(getPosition());
        transformer.rotate(mAngle);
    }

    @Override
    public void clean() {
        super.clean();

        getGameEngine().remove(mSprite);
    }

    @Override
    public void tick() {
        super.tick();

        if (hasWayPoint()) {
            mAngle = getDirection().angle();
        }
    }

    @Override
    public float getSpeed() {
        return super.getSpeed() * mStatic.mSpeedFunction.getValue();
    }
}
