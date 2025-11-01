package io.github.some_example_name;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Main extends ApplicationAdapter {
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private GameState currentState = GameState.RUNNING;

    private SpriteBatch batch;
    private Sound effectSound;
    private Texture playerTexture;
    private Texture objectTexture;
    private Texture pauseTexture;
    private BitmapFont scoreFont;

    private GameWorld world;

    @Override
    public void create() {
        batch = new SpriteBatch();
        effectSound = Gdx.audio.newSound(Gdx.files.internal("drop.mp3"));
        playerTexture = new Texture("t.png");
        objectTexture = new Texture("coin.jpg"));
        pauseTexture = new Texture("pause.png");
        world = new GameWorld(playerTexture, objectTexture, WORLD_WIDTH);

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
        draw();
    }

    private void handlePauseToggle() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            currentState = (currentState == GameState.RUNNING)
                ? GameState.PAUSED
                : GameState.RUNNING;
        }
    }

    private void input(float delta) {
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            world.onPlayerRight(delta);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            world.onPlayerLeft(delta);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            world.onPlayerJump();
        }
    }

    private void logic(float delta) {
        world.update(delta);
    }

    private void draw() {
        batch.begin();
        world.draw(batch);

        scoreFont.draw(batch, "Score: " + world.getScore(), 20, WORLD_HEIGHT - 20);
        scoreFont.draw(batch, "Stage: " + world.getStage(), 20, WORLD_HEIGHT - 60);

        if (currentState == GameState.PAUSED) {
            // 심플 오버레이 텍스트
            scoreFont.draw(batch, "PAUSED (ESC to resume)", WORLD_WIDTH / 2f - 150f, WORLD_HEIGHT / 2f);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        playerTexture.dispose();
        objectTexture.dispose();
        scoreFont.dispose();
        batch.dispose();
        if (pauseTexture != null) pauseTexture.dispose();
        if (effectSound != null) effectSound.dispose();
    }
}
