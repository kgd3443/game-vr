package io.github.some_example_name;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

// Tiled map
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

public class GameWorld {
    // ===== 기본 상수 =====
    private static final float FLOOR_LEVEL = 0f; // 백업용 바닥선

    private static final int COINS_PER_STAGE = 10;
    private static final float SPEED_MULTIPLIER = 2f;

    // ===== 플레이어 =====
    public GameCharacter player;

    // ===== 충돌용 Solid 사각형들 =====
    private final Array<Rectangle> solids = new Array<>();

    // ===== 코인 팝/이펙트 & 사운드 =====
    private final Array<CoinPopEffect> coinEffects = new Array<>();
    private Sound coinSfx; // 코인 획득/팝 사운드

    // ===== 코인 블록(오브젝트/타일 모두 지원) =====
    private static class CoinBlock {
        Rectangle bounds;       // 충돌용 사각형
        boolean active = true;  // 1회성
        CoinBlock(Rectangle r){ this.bounds = r; }
    }
    private final Array<CoinBlock> coinBlocks = new Array<>();

    // ===== 리소스 =====
    private final Texture playerTexture;
    private final Texture coinTexture;   // 코인 팝 이펙트에 사용

    // (초기 코드 호환용) 월드 폭
    private final float worldWidth;

    // ===== 점수/스테이지 =====
    private int score = 0;
    private int stage = 1;

    // ===== Tiled 맵 =====
    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;
    private float unitScale = 1f; // 픽셀=월드픽셀이면 1f

    private int mapWidthPx, mapHeightPx; // 카메라/플레이어 클램프용

    // ===== 생성자 =====
    public GameWorld(Texture playerTex, Texture coinTex, float worldWidth) {
        this.playerTexture = playerTex;
        this.coinTexture   = coinTex;
        this.worldWidth    = worldWidth;

        // --- Tiled 맵 로드 및 크기 계산 ---
        this.tiledMap   = new TmxMapLoader().load("maps/level1.tmx");
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

        // --- 충돌용 Solid 레이어 로드(오브젝트 레이어) ---
        // 필요에 맞게 레이어명을 추가/수정하세요.
        loadSolidRectsFromLayer("Solid");
        loadSolidRectsFromLayer("Ground");
        loadSolidRectsFromLayer("Pipes");
        loadSolidRectsFromLayer("Bricks");
        loadSolidRectsFromLayer("Platforms");
        // ★ 버섯 오브젝트도 충돌(솔리드)로 처리
        loadSolidRectsFromLayer("Mushrooms");

        // --- 코인 블록 로드 (오브젝트 레이어) ---
        loadCoinBlocksFromLayer("CoinBlocks");
        if (coinBlocks.size == 0) loadCoinBlocksFromLayer("Bricks");

        // 코인블록을 solids에도 포함시켜 밟기/머리충돌 모두 되게 함
        for (CoinBlock cb : coinBlocks) {
            if (!containsRectInstance(solids, cb.bounds)) {
                solids.add(cb.bounds);
            }
        }

        // --- 타일 레이어 스캔: tile property 기반으로 solid/coinBlock/mushroomSolid 생성 ---
        scanTileLayersForSolidsAndCoinBlocks();

        // --- 사운드 로드 ---
        try {
            coinSfx = Gdx.audio.newSound(Gdx.files.internal("coin.wav"));
        } catch (Exception e) {
            coinSfx = null; // 사운드가 없어도 게임 진행
        }
    }

    /** 특정 오브젝트 레이어에서 Rectangle 오브젝트를 solids에 수집 */
    private void loadSolidRectsFromLayer(String layerName) {
        MapLayer layer = tiledMap.getLayers().get(layerName);
        if (layer == null) return;
        for (MapObject obj : layer.getObjects()) {
            if (obj instanceof RectangleMapObject) {
                Rectangle r = ((RectangleMapObject)obj).getRectangle();
                solids.add(r); // 참조 그대로 보관 (코인블록과 동일 Rect를 공유 가능)
            }
        }
    }

    /** 코인 블록(오브젝트 레이어) 수집 */
    private void loadCoinBlocksFromLayer(String layerName) {
        MapLayer layer = tiledMap.getLayers().get(layerName);
        if (layer == null) return;
        for (MapObject obj : layer.getObjects()) {
            if (obj instanceof RectangleMapObject) {
                Rectangle r = ((RectangleMapObject)obj).getRectangle();
                coinBlocks.add(new CoinBlock(r)); // r 참조 그대로 저장
            }
        }
    }

    /** 타일 레이어를 훑어 타일 속성(solid/coinBlock/mushroomSolid)으로 충돌/코인블록을 구성 */
    private void scanTileLayersForSolidsAndCoinBlocks() {
        MapLayers layers = tiledMap.getLayers();
        int mapWidthTiles  = (Integer) tiledMap.getProperties().get("width");
        int mapHeightTiles = (Integer) tiledMap.getProperties().get("height");
        int tileWidth      = (Integer) tiledMap.getProperties().get("tilewidth");
        int tileHeight     = (Integer) tiledMap.getProperties().get("tileheight");

        for (int li = 0; li < layers.getCount(); li++) {
            if (!(layers.get(li) instanceof TiledMapTileLayer)) continue;
            TiledMapTileLayer layer = (TiledMapTileLayer) layers.get(li);

            for (int y = 0; y < mapHeightTiles; y++) {
                for (int x = 0; x < mapWidthTiles; x++) {
                    TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                    if (cell == null || cell.getTile() == null) continue;

                    MapProperties props = cell.getTile().getProperties();
                    boolean isSolid        = props.containsKey("solid")        && Boolean.TRUE.equals(props.get("solid", Boolean.class));
                    boolean isCoinBlock    = props.containsKey("coinBlock")    && Boolean.TRUE.equals(props.get("coinBlock", Boolean.class));
                    boolean isMushroomSol  = props.containsKey("mushroomSolid")&& Boolean.TRUE.equals(props.get("mushroomSolid", Boolean.class));
                    if (!isSolid && !isCoinBlock && !isMushroomSol) continue;

                    float rx = x * tileWidth;
                    float ry = y * tileHeight;
                    Rectangle rect = new Rectangle(rx, ry, tileWidth, tileHeight);

                    // 충돌(밟기/막기)용 solids에 추가
                    solids.add(rect);

                    // 코인 블록이면 논리 등록(시각은 타일 그대로 유지)
                    if (isCoinBlock) coinBlocks.add(new CoinBlock(rect));
                    // 버섯 타일은 solids만으로 충분 (추가 논리는 이후 확장 시)
                }
            }
        }
    }

    /** 배열에 동일 인스턴스가 있는지 검사(참조 동일성) */
    private boolean containsRectInstance(Array<Rectangle> arr, Rectangle target) {
        for (Rectangle r : arr) if (r == target) return true;
        return false;
    }

    // ===== 메인 업데이트 =====
    public void update(float delta) {
        // (1) 플레이어 물리/타이머 선적용
        player.preUpdate(delta);

        // (2) 예측 위치
        float playerW = player.sprite.getWidth();
        float playerH = player.sprite.getHeight();
        float oldX = player.position.x;
        float oldY = player.position.y;

        float targetX = oldX + player.velocity.x * delta;
        float targetY = oldY + player.velocity.y * delta;

        // (3) X축 충돌 해결
        Rectangle playerRectX = new Rectangle(targetX, oldY, playerW, playerH);
        if (player.velocity.x != 0f) {
            for (Rectangle r : solids) {
                if (playerRectX.overlaps(r)) {
                    if (player.velocity.x > 0f) targetX = r.x - playerW;
                    else                        targetX = r.x + r.width;
                    player.velocity.x = 0f;
                    playerRectX.setX(targetX);
                }
            }
        }

        // (4) Y축 충돌 해결 (+ 머리 충돌 시 코인 처리)
        boolean groundedNow = false;
        Rectangle playerRectY = new Rectangle(targetX, targetY, playerW, playerH);

        if (player.velocity.y != 0f) {
            if (player.velocity.y > 0f) {
                // 위로 이동(점프/상승)
                for (Rectangle r : solids) {
                    if (playerRectY.overlaps(r)) {
                        boolean crossedFromBelow = (oldY + playerH) <= r.y && (targetY + playerH) >= r.y;
                        targetY = r.y - playerH;
                        player.velocity.y = 0f;
                        playerRectY.setY(targetY);

                        if (crossedFromBelow) {
                            CoinBlock hitBlock = findCoinBlockByRect(r);
                            if (hitBlock != null && hitBlock.active) {
                                float blockCenterX = r.x + r.width / 2f;
                                float blockTopY    = r.y + r.height;
                                coinEffects.add(new CoinPopEffect(coinTexture, blockCenterX, blockTopY));
                                playCoinSfx();
                                onCoinCollected(1);
                                hitBlock.active = false; // 블록은 남고, 재히트만 방지
                            }
                        }
                    }
                }
            } else {
                // 아래로 이동(낙하)
                for (Rectangle r : solids) {
                    if (playerRectY.overlaps(r)) {
                        targetY = r.y + r.height; // 상면에 착지
                        player.velocity.y = 0f;
                        groundedNow = true;
                        playerRectY.setY(targetY);
                    }
                }
            }
        } else {
            // y 속도 0이어도 바닥에 붙여야 하는 경우(경계 이슈 대비)
            for (Rectangle r : solids) {
                if (playerRectY.overlaps(r)) {
                    targetY = r.y + r.height;
                    groundedNow = true;
                    playerRectY.setY(targetY);
                }
            }
        }

        // (5) 백업용 바닥선 처리(솔리드가 전혀 없을 때)
        if (targetY < FLOOR_LEVEL) {
            targetY = FLOOR_LEVEL;
            player.velocity.y = Math.max(player.velocity.y, 0f);
            groundedNow = true;
        }

        // (6) 점프 버퍼/코요테 소비 → 점프
        player.setGrounded(groundedNow);
        player.tryConsumeJump();

        // (7) 경계 클램프 (맵 너비/높이 기준)
        targetX = MathUtils.clamp(targetX, 0, Math.max(0, mapWidthPx - playerW));
        targetY = MathUtils.clamp(targetY, 0, Math.max(0, mapHeightPx - playerH));

        // (8) 최종 위치 반영 및 스프라이트 동기화
        player.position.set(targetX, targetY);
        player.syncSpriteToPosition();

        // (9) 점프 컷(키 뗐을 때 상승속도 줄이기)
        player.postCollisionUpdate();

        // (10) 코인 팝 이펙트 업데이트/정리
        for (int i = coinEffects.size - 1; i >= 0; i--) {
            CoinPopEffect fx = coinEffects.get(i);
            fx.update(delta);
            if (!fx.alive) coinEffects.removeIndex(i);
        }
    }

    private CoinBlock findCoinBlockByRect(Rectangle r) {
        for (CoinBlock cb : coinBlocks) if (cb.bounds == r) return cb;
        return null;
    }

    private void playCoinSfx() {
        if (coinSfx != null) {
            try { coinSfx.play(0.8f); } catch (Exception ignored) {}
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
        score = 0; // 스테이지 진급 시 한 번만 초기화
        // (추후 난이도 요소에 사용할 수 있음)
    }

    // ===== 입력 위임 =====
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
    public GameCharacter getPlayer() { return player; }
    public int getScore() { return score; }
    public int getStage() { return stage; }

    // ===== 렌더 =====
    public void draw(SpriteBatch batch) {
        player.draw(batch);
        for (CoinPopEffect fx : coinEffects) fx.draw(batch);
    }

    // ===== 리소스 해제 =====
    public void dispose() {
        if (mapRenderer != null) mapRenderer.dispose();
        if (tiledMap != null)    tiledMap.dispose();
        if (coinSfx != null)     coinSfx.dispose();
    }
}
