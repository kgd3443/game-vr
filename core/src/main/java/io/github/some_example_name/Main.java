package io.github.some_example_name;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

public class Main extends ApplicationAdapter {
    private OrthographicCamera cam;
    private ShapeRenderer sr;
    private SpriteBatch batch;
    private BitmapFont font;

    private GameWorld world;

    // Shake state
    private boolean shaking = false;
    private float shakeTimer = 0f;

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
        // Restart level (R) - resets points to avoid farming
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            world.restartLevel(true); // 포인트 초기화 재시작
        }
        if (world.state.paused || shaking) return; // 흔들리는 동안/일시정지 동안 입력차단

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

        // dash (fixed distance: 5 tiles). Cost = 1 point
        if ((Gdx.input.isKeyJustPressed(Input.Keys.X) || Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT))
            && !world.player.dashing && world.state.point >= Constants.DASH_COST) {
            world.state.point -= Constants.DASH_COST; // 포인트 차감

            int dir = 1;
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) dir = -1;
            else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) dir = 1;
            else if (world.player.vel.x < 0) dir = -1; // 입력 없으면 마지막 진행방향 유지

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

        // 낙사 감지되면 흔들림 시작
        if (world.fellThisFrame && !shaking) {
            shaking = true;
            shakeTimer = Constants.SHAKE_DURATION;
        }

        if (!world.state.paused && !shaking) {
            handleInput(dt);
            world.step(dt);
        } else {
            // 흔들림/일시정지 중에도 ESC/P, R은 동작
            handleInput(dt);
        }

        // camera follow base position
        float baseX = Math.max(cam.viewportWidth / 2f, world.player.pos.x + 100);
        float baseY = cam.viewportHeight / 2f;

        // apply shake
        float offsetX = 0f, offsetY = 0f;
        if (shaking) {
            shakeTimer -= dt;
            float t = Math.max(0f, shakeTimer) / Constants.SHAKE_DURATION; // 1 -> 0
            float amp = Constants.SHAKE_AMPLITUDE * t * t; // ease-out
            offsetX = MathUtils.random(-amp, amp);
            offsetY = MathUtils.random(-amp, amp);
            if (shakeTimer <= 0f) {
                shaking = false;
                world.restartLevel(true);
            }
        }

        cam.position.x = baseX + offsetX;
        cam.position.y = baseY + offsetY;
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
        font.draw(batch,
            (world.state.paused ? "[PAUSED] " : "") +
                "Point: " + world.state.point +
                "   Level: " + world.state.currentLevel +
                "   [Z]Jump x2  [X]Dash(cost 1, 5 tiles)  [R]Restart  [ESC/P]Pause  [1~3]Level",
            cam.position.x - 380, cam.viewportHeight - 12);
        if (shaking) {
            font.draw(batch, "Fell! Restarting...", cam.position.x - 80, cam.viewportHeight - 32);
        }
        batch.end();
    }

    @Override public void dispose() {
        sr.dispose();
        batch.dispose();
        font.dispose();
    }
}
