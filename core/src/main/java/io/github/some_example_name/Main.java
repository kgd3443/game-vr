package io.github.some_example_name;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Main extends ApplicationAdapter {
    private OrthographicCamera cam;
    private ShapeRenderer sr;
    private SpriteBatch batch;
    private BitmapFont font;

    private GameWorld world;

    @Override
    public void create() {
        cam = new OrthographicCamera();
        cam.setToOrtho(false, Constants.V_WIDTH, Constants.V_HEIGHT);
        sr = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        world = new GameWorld();
    }

    private void handleInput(float dt) {
        // Pause toggle (ESC or P)
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            world.state.paused = !world.state.paused;
        }
        // Restart level (R) - resets score to avoid farming
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            world.restartLevel(true);
        }
        if (world.state.paused) return;

        float ax = 0f;
if (!world.player.dashing) {
    if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) ax -= Constants.MOVE_SPEED;
    if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) ax += Constants.MOVE_SPEED;
    world.player.vel.x = ax;
}

        // jump (double jump)
        if (Gdx.input.isKeyJustPressed(Input.Keys.Z) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (world.player.jumpsLeft > 0) {
                world.player.vel.y = Constants.JUMP_VELOCITY;
                world.player.jumpsLeft--;
            }
        }

        // dash (fixed distance ~ 5 tiles). Cost = DASH_COST
        if ((Gdx.input.isKeyJustPressed(Input.Keys.X) || Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT))
            && !world.player.dashing && world.state.score >= Constants.DASH_COST) {
            world.state.score -= Constants.DASH_COST;

            int dir = 1;
if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) dir = -1;
else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) dir = 1;
else if (world.player.vel.x < 0) dir = -1;
            world.player.startDash(dir);
        }

        // level switching (dev hotkeys)
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) world.loadLevel(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) world.loadLevel(2);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) world.loadLevel(3);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        handleInput(dt);
        if (!world.state.paused) world.step(dt);

        // camera follow X
        cam.position.x = Math.max(cam.viewportWidth / 2f, world.player.pos.x + 100);
        cam.update();

        Gdx.gl.glClearColor(0.1f, 0.12f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // draw world
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < world.blocks.size; i++) {
            world.blocks.get(i).draw(sr);
        }
        world.player.draw(sr);
        sr.end();

        // UI
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        font.draw(batch, "Score: " + world.state.score + "   Lives: " + world.state.lives +
                "   Level: " + world.state.currentLevel + "   [Z]Jump x2  [X]Dash(5 tiles)  [R]Restart  [ESC/P]Pause  [1~3]Level",
                cam.position.x - 380, cam.viewportHeight - 12);
        batch.end();
    }

    @Override public void dispose() {
        sr.dispose();
        batch.dispose();
        font.dispose();
    }
}