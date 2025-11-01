package io.github.some_example_name;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.Vector2;

public class GameWorld {
    private static final float WORLD_GRAVITY = -9.8f * 200f;
    private static final float FLOOR_LEVEL = 0f;

    private static final float OBJECT_SPAWN_TIME = 2.0f;
    private float objectSpawnTimer = OBJECT_SPAWN_TIME;

    private static final int COINS_PER_STAGE = 10;
    private static final float SPEED_MULTIPLIER = 2f;

    public GameCharacter player;
    private final Array<CoinObject> objects = new Array<>();

    private final Texture playerTexture;
    private final Texture objectTexture;
    private final float worldWidth;

    private int score = 0;
    private int stage = 1;

    // 초기 낙하 속도(음수)
    private float speed = -500f;

    public GameWorld(Texture playerTex, Texture objectTex, float worldWidth) {
        this.playerTexture = playerTex;
        this.objectTexture = objectTex;
        this.worldWidth = worldWidth;

        this.player = new GameCharacter(playerTexture, 100, FLOOR_LEVEL);
        this.player.isGrounded = true;
    }

    public void update(float delta) {
        // 1) 중력 적용
        player.velocity.y += WORLD_GRAVITY * delta;

        // 2) 스폰 타이머
        updateSpawning(delta);

        // 3) 예측 위치
        float newX = player.position.x + player.velocity.x * delta;
        float newY = player.position.y + player.velocity.y * delta;

        // 4) 코인 업데이트/정리
        for (int i = objects.size - 1; i >= 0; i--) {
            CoinObject obj = objects.get(i);
            obj.update(delta);
            if (obj.position.y < FLOOR_LEVEL - obj.sprite.getHeight()) {
                objects.removeIndex(i);
            }
        }

        // 5) 바닥 충돌
        if (newY <= FLOOR_LEVEL) {
            newY = FLOOR_LEVEL;
            player.velocity.y = 0;
            player.isGrounded = true;
        } else {
            player.isGrounded = false;
        }

        // 6) 플레이어-코인 충돌
        for (int i = objects.size - 1; i >= 0; i--) {
            CoinObject obj = objects.get(i);
            if (player.sprite.getBoundingRectangle().overlaps(obj.bounds)) {
                onCoinCollected(1);
                objects.removeIndex(i);
            }
        }

        // 7) 화면 경계로 X 클램프
        newX = MathUtils.clamp(newX, 0, worldWidth - player.sprite.getWidth());

        // 8) 최종 위치 반영 및 스프라이트 동기화
        player.position.set(newX, newY);
        player.syncSpriteToPosition();
    }

    private void updateSpawning(float delta) {
        objectSpawnTimer -= delta;
        if (objectSpawnTimer <= 0f) {
            objectSpawnTimer = OBJECT_SPAWN_TIME;
            float coinWidth = 90f;
            float startX = (float) Math.random() * (worldWidth - coinWidth);
            float startY = 720f;
            objects.add(new CoinObject(objectTexture, startX, startY, speed));
        }
    }

    private void onCoinCollected(int amount) {
        score += amount;
        if (score >= COINS_PER_STAGE) {
            nextStage();
        }
    }

    private void nextStage() {
        stage++;
        score = 0;               // 스테이지 진급 시 한 번만 초기화
        speed *= SPEED_MULTIPLIER;
    }

    // 입력 위임 (delta 기반)
    public void onPlayerJump() { player.jump(); }
    public void onPlayerRight(float delta) { player.moveRight(delta); }
    public void onPlayerLeft (float delta) { player.moveLeft(delta); }

    // 렌더/조회
    public void draw(SpriteBatch batch) {
        player.draw(batch);
        for (CoinObject obj : objects) obj.draw(batch);
    }
    public GameCharacter getPlayer() { return player; }
    public Array<CoinObject> getObjects() { return objects; }
    public int getScore() { return score; }
    public int getStage() { return stage; }
}
