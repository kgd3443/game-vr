package io.github.some_example_name;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TiledMapTile;


// Tiled map
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

public class GameWorld {
    // ===== 기본 상수 =====
    private static final float FLOOR_LEVEL = 0f;

    private static final float OBJECT_SPAWN_TIME = 2.0f;
    private float objectSpawnTimer = OBJECT_SPAWN_TIME;

    private static final int COINS_PER_STAGE = 10;
    private static final float SPEED_MULTIPLIER = 2f;

    // ===== 플레이어/오브젝트 =====
    public GameCharacter player;
    private final Array<CoinObject> objects = new Array<>();

    // ===== 리소스 =====
    private final Texture playerTexture;
    private final Texture objectTexture;

    // (초기 코드 호환용) 월드 폭
    private final float worldWidth;

    // ===== 점수/스테이지 =====
    private int score = 0;
    private int stage = 1;

    // 코인 낙하 초기 속도(음수)
    private float speed = -500f;

    // ===== Tiled 맵 =====
    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;
    private float unitScale = 1f; // 픽셀=월드픽셀이면 1f

    private int mapWidthPx, mapHeightPx; // 카메라/플레이어 클램프용

    // ===== 생성자 =====
    public GameWorld(Texture playerTex, Texture objectTex, float worldWidth) {
        this.playerTexture = playerTex;
        this.objectTexture = objectTex;
        this.worldWidth = worldWidth;

        // --- Tiled 맵 로드 및 크기 계산 ---
        // assets/maps/level1.tmx 기준
        this.tiledMap = new TmxMapLoader().load("maps/level1.tmx");
        this.mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, unitScale);

        int mapWidthTiles  = (Integer) tiledMap.getProperties().get("width");
        int mapHeightTiles = (Integer) tiledMap.getProperties().get("height");
        int tileWidth      = (Integer) tiledMap.getProperties().get("tilewidth");
        int tileHeight     = (Integer) tiledMap.getProperties().get("tileheight");
        this.mapWidthPx  = mapWidthTiles * tileWidth;
        this.mapHeightPx = mapHeightTiles * tileHeight;

        for (TiledMapTileSet set : tiledMap.getTileSets()) {
            // 같은 타일셋의 텍스처는 보통 하나이므로, 첫 타일만 처리하고 break
            for (TiledMapTile tile : set) {
                if (tile != null && tile.getTextureRegion() != null) {
                    tile.getTextureRegion().getTexture()
                        .setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                    break;
                }
            }
        }

        // --- 플레이어 초기화 ---
        this.player = new GameCharacter(playerTex, 100, FLOOR_LEVEL);
        this.player.setGrounded(true);

        // 플레이어 스프라이트를 타일 기준 크기로 축소 (1x2 타일: 16x32)
        this.player.sprite.setSize(16f, 32f);
        this.player.syncSpriteToPosition();
    }

    // ===== 메인 업데이트 =====
    public void update(float delta) {
        // (1) 플레이어 물리/타이머 선적용
        player.preUpdate(delta);

        // (2) 스폰 타이머
        updateSpawning(delta);

        // (3) 예측 위치
        float newX = player.position.x + player.velocity.x * delta;
        float newY = player.position.y + player.velocity.y * delta;

        // (4) 코인 업데이트/정리
        for (int i = objects.size - 1; i >= 0; i--) {
            CoinObject obj = objects.get(i);
            obj.update(delta);
            if (obj.position.y < FLOOR_LEVEL - obj.sprite.getHeight()) {
                objects.removeIndex(i);
            }
        }

        // (5) 바닥 충돌 처리
        boolean groundedNow = false;
        if (newY <= FLOOR_LEVEL) {
            newY = FLOOR_LEVEL;
            // 하향 속도만 막음(상향값은 유지)
            player.velocity.y = Math.max(player.velocity.y, 0f);
            groundedNow = true;
        }
        player.setGrounded(groundedNow);

        // (6) 점프 버퍼/코요테 소비 → 점프 실행
        player.tryConsumeJump();

        // (7) 플레이어-코인 충돌
        for (int i = objects.size - 1; i >= 0; i--) {
            CoinObject obj = objects.get(i);
            if (player.sprite.getBoundingRectangle().overlaps(obj.bounds)) {
                onCoinCollected(1);
                objects.removeIndex(i);
            }
        }

        // (8) X 경계 클램프 (맵 너비 기준)
        float playerW = player.sprite.getWidth();
        newX = MathUtils.clamp(newX, 0, Math.max(0, mapWidthPx - playerW));

        // (9) 최종 위치 반영 및 스프라이트 동기화
        player.position.set(newX, newY);
        player.syncSpriteToPosition();

        // (10) 점프 컷(키 뗐을 때 상승속도 줄이기)
        player.postCollisionUpdate();
    }

    // ===== 코인 스폰 =====
    private void updateSpawning(float delta) {
        objectSpawnTimer -= delta;
        if (objectSpawnTimer <= 0f) {
            objectSpawnTimer = OBJECT_SPAWN_TIME;

            float coinWidth = 90f;  // CoinObject와 동일 가정
            float startX = (float) Math.random() * Math.max(1f, (mapWidthPx - coinWidth));
            float startY = mapHeightPx; // 맵 최상단에서 떨어뜨림
            objects.add(new CoinObject(objectTexture, startX, startY, speed));
        }
    }

    // ===== 점수/스테이지 =====
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

    // ===== 입력 위임 (1단계용) =====
    public void onPlayerMoveAxis(float axis) { player.setMoveAxis(axis); }
    public void onPlayerJumpPressed() { player.onJumpPressed(); }
    public void onPlayerJumpReleased() { player.onJumpReleased(); }

    // ===== 맵 렌더 (2단계 4번) =====
    public void renderMap(OrthographicCamera camera) {
        mapRenderer.setView(camera);
        mapRenderer.render();
    }

    // ===== Getter (2단계 5번) =====
    public int getMapWidthPx()  { return mapWidthPx; }
    public int getMapHeightPx() { return mapHeightPx; }

    // ===== 렌더/조회 =====
    public void draw(SpriteBatch batch) {
        player.draw(batch);
        for (CoinObject obj : objects) obj.draw(batch);
    }
    public GameCharacter getPlayer() { return player; }
    public Array<CoinObject> getObjects() { return objects; }
    public int getScore() { return score; }
    public int getStage() { return stage; }

    // ===== 리소스 해제 (2단계 6번) =====
    public void dispose() {
        if (mapRenderer != null) mapRenderer.dispose();
        if (tiledMap != null)    tiledMap.dispose();
    }
}
