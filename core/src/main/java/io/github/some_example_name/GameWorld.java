package io.github.some_example_name;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

// Tiled map
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;

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

    // ===== 코인 블록(오브젝트 레이어) =====
    private static class CoinBlock {
        Rectangle bounds;   // TMX 오브젝트의 위치/크기
        boolean active = true; // 1회성
        CoinBlock(Rectangle r){ this.bounds = new Rectangle(r); }
    }
    private final Array<CoinBlock> coinBlocks = new Array<>();

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
        this.tiledMap = new TmxMapLoader().load("maps/level1.tmx");
        this.mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, unitScale);

        int mapWidthTiles  = (Integer) tiledMap.getProperties().get("width");
        int mapHeightTiles = (Integer) tiledMap.getProperties().get("height");
        int tileWidth      = (Integer) tiledMap.getProperties().get("tilewidth");
        int tileHeight     = (Integer) tiledMap.getProperties().get("tileheight");
        this.mapWidthPx  = mapWidthTiles * tileWidth;
        this.mapHeightPx = mapHeightTiles * tileHeight;

        // 타일셋 텍스처 필터: Nearest
        for (TiledMapTileSet set : tiledMap.getTileSets()) {
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
        // 1x2 타일 크기
        this.player.sprite.setSize(16f, 32f);
        this.player.syncSpriteToPosition();

        // --- 코인 블록 로드 (오브젝트 레이어: "CoinBlocks" 또는 "Bricks") ---
        loadCoinBlocksFromLayer("CoinBlocks");
        if (coinBlocks.size == 0) loadCoinBlocksFromLayer("Bricks");
    }

    private void loadCoinBlocksFromLayer(String layerName) {
        MapLayer layer = tiledMap.getLayers().get(layerName);
        if (layer == null) return;
        for (MapObject obj : layer.getObjects()) {
            if (obj instanceof RectangleMapObject) {
                Rectangle r = ((RectangleMapObject)obj).getRectangle();
                coinBlocks.add(new CoinBlock(r));
            }
        }
    }

    // ===== 메인 업데이트 =====
    public void update(float delta) {
        // (1) 플레이어 물리/타이머 선적용
        player.preUpdate(delta);

        // (2) 스폰 타이머
        updateSpawning(delta);

        // (3) 예측 위치
        float playerW = player.sprite.getWidth();
        float playerH = player.sprite.getHeight();
        float oldX = player.position.x;
        float oldY = player.position.y;
        float newX = oldX + player.velocity.x * delta;
        float newY = oldY + player.velocity.y * delta;

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
            player.velocity.y = Math.max(player.velocity.y, 0f);
            groundedNow = true;
        }

        // (6) 코인 블록 '머리 충돌' 처리 (위로 이동 중일 때만)
        if (player.velocity.y > 0f) {
            // 플레이어 AABB (old/new)
            float oldTop = oldY + playerH;
            float newTop = newY + playerH;

            for (int i = 0; i < coinBlocks.size; i++) {
                CoinBlock b = coinBlocks.get(i);
                if (!b.active) continue;

                Rectangle r = b.bounds;
                // X 축으로 겹치고, "아래→위로 블록 하단(r.y)을 관통"했는지 체크
                boolean xOverlap = (newX < r.x + r.width) && (newX + playerW > r.x);
                boolean crossedFromBelow = (oldTop <= r.y) && (newTop >= r.y);

                if (xOverlap && crossedFromBelow) {
                    // 머리로 블록을 쳤다 → 머리를 블록 하단에 붙이고 상승속도 제거
                    newY = r.y - playerH;
                    player.velocity.y = 0f;

                    // 코인 획득 & 블록 비활성화(1회성)
                    onCoinCollected(1);
                    b.active = false;
                    // 필요하면 배열에서 제거: coinBlocks.removeIndex(i--);
                    break; // 한 프레임에 한 개만 처리
                }
            }
        }

        // (7) 점프 버퍼/코요테 소비 → 점프 실행
        player.setGrounded(groundedNow);
        player.tryConsumeJump();

        // (8) 플레이어-코인(떨어지는 오브젝트) 충돌
        for (int i = objects.size - 1; i >= 0; i--) {
            CoinObject obj = objects.get(i);
            if (player.sprite.getBoundingRectangle().overlaps(obj.bounds)) {
                onCoinCollected(1);
                objects.removeIndex(i);
            }
        }

        // (9) X 경계 클램프 (맵 너비 기준)
        newX = MathUtils.clamp(newX, 0, Math.max(0, mapWidthPx - playerW));

        // (10) 최종 위치 반영 및 스프라이트 동기화
        player.position.set(newX, newY);
        player.syncSpriteToPosition();

        // (11) 점프 컷(키 뗐을 때 상승속도 줄이기)
        player.postCollisionUpdate();
    }

    // ===== 코인 스폰(기존 낙하 코인) =====
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

    // ===== 맵 렌더 =====
    public void renderMap(OrthographicCamera camera) {
        mapRenderer.setView(camera);
        mapRenderer.render();
    }

    // ===== Getter =====
    public int getMapWidthPx()  { return mapWidthPx; }
    public int getMapHeightPx() { return mapHeightPx; }

    // ===== 렌더/조회 =====
    public void draw(SpriteBatch batch) {
        player.draw(batch);
        for (CoinObject obj : objects) obj.draw(batch);
        // 코인 블록은 오브젝트 레이어라 별도 렌더 없음(타일 레이어로 시각은 이미 표현됨)
    }
    public GameCharacter getPlayer() { return player; }
    public Array<CoinObject> getObjects() { return objects; }
    public int getScore() { return score; }
    public int getStage() { return stage; }

    // ===== 리소스 해제 =====
    public void dispose() {
        if (mapRenderer != null) mapRenderer.dispose();
        if (tiledMap != null)    tiledMap.dispose();
    }
}
