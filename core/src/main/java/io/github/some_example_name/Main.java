package io.github.some_example_name;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class Main extends ApplicationAdapter {
    // TMX(16x16, 13타일 높이=208px)에 맞춘 월드 해상도
    private static final float VIEW_W  = 320f; // 가로 20타일
    private static final float VIEW_H  = 208f; // 세로 13타일(맵 높이와 동일)

    private GameState currentState = GameState.RUNNING;

    private SpriteBatch batch;
    private Sound effectSound;
    private Texture playerTexture;
    private Texture objectTexture;
    private Texture pauseTexture;
    private BitmapFont scoreFont;

    private GameWorld world;

    // 카메라/뷰포트
    private OrthographicCamera camera;
    private Viewport viewport;

    // 가변 점프를 위한 키 홀드 상태
    private boolean jumpHeld = false;

    @Override
    public void create() {
        batch = new SpriteBatch();
        effectSound   = Gdx.audio.newSound(Gdx.files.internal("drop.mp3"));
        playerTexture = new Texture("t.png");
        objectTexture = new Texture("coin.jpg");
        pauseTexture  = new Texture("pause.png");

        // 픽셀아트 선명도(선택 권장)
        playerTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        objectTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // 카메라/뷰포트: TMX 스케일에 맞춤
        camera   = new OrthographicCamera();
        viewport = new FitViewport(VIEW_W, VIEW_H, camera);
        camera.position.set(VIEW_W / 2f, VIEW_H / 2f, 0f);
        camera.update();

        // 월드 생성(타일맵은 GameWorld에서 로드)
        world = new GameWorld(playerTexture, objectTexture, VIEW_W);

        // 폰트
        scoreFont = new BitmapFont();
        scoreFont.getData().setScale(1.0f); // VIEW가 작아졌으니 글자 크기도 조정
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float delta = Gdx.graphics.getDeltaTime();
        handlePauseToggle();

        if (currentState == GameState.RUNNING) {
            input(delta);
            logic(delta);
        }

        // 카메라를 플레이어에 맞춰 갱신
        updateCamera(delta);

        // 렌더 순서: 타일맵 → 엔티티/텍스트
        world.renderMap(camera);

        batch.setProjectionMatrix(camera.combined);
        draw();
    }

    private void handlePauseToggle() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            currentState = (currentState == GameState.RUNNING)
                ? GameState.PAUSED
                : GameState.RUNNING;
        }
    }

    // 좌우 축 입력 + 점프 눌림/떼짐 처리
    private void input(float delta) {
        // 좌우 축 입력(A/D 지원)
        float axis = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)  || Gdx.input.isKeyPressed(Input.Keys.A))  axis -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D))  axis += 1f;
        world.onPlayerMoveAxis(axis);

        // 점프(눌림/떼짐 분리: 가변 점프용)
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            world.onPlayerJumpPressed();
            jumpHeld = true;
        }
        boolean nowHeld = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        if (jumpHeld && !nowHeld) { // 키를 뗀 프레임에 1회만 전달
            world.onPlayerJumpReleased();
            jumpHeld = false;
        }
    }

    private void logic(float delta) {
        world.update(delta);
    }

    // 플레이어 추적 + 맵 경계 내 클램프
    private void updateCamera(float delta) {
        Vector2 p = world.getPlayer().position;
        float px = p.x + world.getPlayer().sprite.getWidth()  / 2f;
        float py = p.y + world.getPlayer().sprite.getHeight() / 2f;

        // 부드러운 추적(lerp)
        float lerp = 5f;
        camera.position.x += (px - camera.position.x) * lerp * delta;
        camera.position.y += (py - camera.position.y) * lerp * delta;

        // 맵 경계로 카메라 클램프
        float halfW = viewport.getWorldWidth()  / 2f;
        float halfH = viewport.getWorldHeight() / 2f;
        float maxX  = world.getMapWidthPx()  - halfW;
        float maxY  = world.getMapHeightPx() - halfH;

        camera.position.x = MathUtils.clamp(camera.position.x, halfW, Math.max(halfW, maxX));
        camera.position.y = MathUtils.clamp(camera.position.y, halfH, Math.max(halfH, maxY));

        camera.update();
    }

    private void draw() {
        batch.begin();

        // 엔티티
        world.draw(batch);

        // HUD (지금은 카메라 좌표계로 함께 스크롤됨)
        scoreFont.draw(batch, "Score: " + world.getScore(), camera.position.x - VIEW_W / 2f + 8f, camera.position.y + VIEW_H / 2f - 8f);
        scoreFont.draw(batch, "Stage: " + world.getStage(), camera.position.x - VIEW_W / 2f + 8f, camera.position.y + VIEW_H / 2f - 24f);

        if (currentState == GameState.PAUSED) {
            scoreFont.draw(batch, "PAUSED (ESC to resume)",
                camera.position.x - 80f, camera.position.y);
        }

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true); // true = 카메라 중심 유지
    }

    @Override
    public void dispose() {
        // GameWorld 내부 타일맵/렌더러도 해제
        if (world != null) world.dispose();

        if (playerTexture != null) playerTexture.dispose();
        if (objectTexture != null) objectTexture.dispose();
        if (pauseTexture  != null) pauseTexture.dispose();
        if (effectSound   != null) effectSound.dispose();

        if (scoreFont != null) scoreFont.dispose();
        if (batch != null) batch.dispose();
    }
}
