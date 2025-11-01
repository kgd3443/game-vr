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
    private static final float WORLD_WIDTH  = 1280f;
    private static final float WORLD_HEIGHT = 720f;

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

        // 카메라/뷰포트 준비
        camera   = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f);
        camera.update();

        // 월드 생성(타일맵은 GameWorld에서 로드)
        world = new GameWorld(playerTexture, objectTexture, WORLD_WIDTH);

        // 폰트
        scoreFont = new BitmapFont();
        scoreFont.getData().setScale(2f);
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
        scoreFont.draw(batch, "Score: " + world.getScore(), 20, WORLD_HEIGHT - 20);
        scoreFont.draw(batch, "Stage: " + world.getStage(), 20, WORLD_HEIGHT - 60);

        if (currentState == GameState.PAUSED) {
            scoreFont.draw(batch, "PAUSED (ESC to resume)",
                camera.position.x - 150f, camera.position.y);
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
