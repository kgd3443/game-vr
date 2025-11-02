package io.github.some_example_name;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Iterator;

public class Main extends ApplicationAdapter {
    private OrthographicCamera cam;
    private ShapeRenderer sr;
    private SpriteBatch batch;
    private BitmapFont font;
    private GlyphLayout layout;

    private GameWorld world;

    private boolean shaking = false;
    private float shakeTimer = 0f;

    // 클리어 GIF
    private com.badlogic.gdx.utils.Array<Texture> clearGifTextures;
    private com.badlogic.gdx.utils.Array<TextureRegion> clearGifRegions;
    private float clearGifFrameDuration = 0.06f;
    private float clearGifTimer = 0f;

    @Override
    public void create() {
        cam = new OrthographicCamera();
        cam.setToOrtho(false, Constants.V_WIDTH, Constants.V_HEIGHT);
        sr = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();   // 영어 메시지
        layout = new GlyphLayout();

        // 에셋 로드
        Assets.load();

        // 클리어 GIF
        loadClearGif("clear.gif");

        world = new GameWorld();
    }

    private void handleInput(float dt) {
        if (world.state.cleared) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                world.loadLevel(1);
                world.state.point = 0;
                clearGifTimer = 0f;
            }
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            world.state.paused = !world.state.paused;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            world.restartLevel(true);
        }
        if (world.state.paused || shaking) return;

        float ax = 0f;
        boolean left  = Gdx.input.isKeyPressed(Input.Keys.LEFT)  || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);

        if (!world.player.dashing) {
            if (left)  ax -= Constants.MOVE_SPEED;
            if (right) ax += Constants.MOVE_SPEED;
            if (ax != 0f) world.player.vel.x = ax;
            else {
                if (world.onSlippery && world.player.grounded) {
                    world.player.vel.x *= Constants.SLIPPERY_DECAY;
                    if (Math.abs(world.player.vel.x) < 1f) world.player.vel.x = 0f;
                } else {
                    world.player.vel.x = 0f;
                }
            }
        }

        // 점프(2단)
        if (Gdx.input.isKeyJustPressed(Input.Keys.Z) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (world.player.jumpsLeft > 0) {
                world.player.vel.y = Constants.JUMP_VELOCITY;
                world.player.jumpsLeft--;
            }
        }

        // 대시(5타일, cost=1)
        if ((Gdx.input.isKeyJustPressed(Input.Keys.X) || Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT))
            && !world.player.dashing && world.state.point >= Constants.DASH_COST) {
            world.state.point -= Constants.DASH_COST;
            int dir = right ? 1 : (left ? -1 : (world.player.vel.x < 0 ? -1 : 1));
            world.player.startDash(dir);
        }

        // 테스트 단축키
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) world.loadLevel(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) world.loadLevel(2);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) world.loadLevel(3);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        // 낙사 흔들림 시작
        if (!world.state.cleared && world.fellThisFrame && !shaking) {
            shaking = true;
            shakeTimer = Constants.SHAKE_DURATION;
        }

        if (world.state.cleared) {
            handleInput(dt);
            clearGifTimer += dt;
        } else if (!world.state.paused && !shaking) {
            handleInput(dt);
            world.step(dt);
        } else {
            handleInput(dt);
        }

        float baseX = Math.max(cam.viewportWidth / 2f, world.player.pos.x + 100);
        float baseY = cam.viewportHeight / 2f;

        float offsetX = 0f, offsetY = 0f;
        if (!world.state.cleared && shaking) {
            shakeTimer -= dt;
            float t = Math.max(0f, shakeTimer) / Constants.SHAKE_DURATION;
            float amp = Constants.SHAKE_AMPLITUDE * t * t;
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

        // ===== 1) 월드 텍스처 먼저 그리기 =====
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        if (!world.state.cleared) {
            world.draw(batch); // 타일 텍스처
        } else {
            // 클리어 화면: GIF 먼저
            TextureRegion frame = getGifFrame(clearGifTimer);
            if (frame != null) {
                float scale = 1f;
                float gifW = frame.getRegionWidth() * scale;
                float gifH = frame.getRegionHeight() * scale;
                float gifX = cam.position.x - gifW / 2f;
                float gifY = cam.viewportHeight * 0.56f - gifH / 2f;
                batch.draw(frame, gifX, gifY, gifW, gifH);
            }
        }
        batch.end();

        // ===== 2) 플레이어/보완용 사각형(미끄럼 이미지 없을 때) =====
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        if (!world.state.cleared) {
            // 미끄럼 이미지 없을 때 보완 렌더
            for (int i = 0; i < world.blocks.size; i++) {
                world.blocks.get(i).drawShape(sr);
            }
            world.player.draw(sr);
        } else {
            drawCrownedHeroLeft();
        }
        sr.end();

        // ===== 3) UI + 클리어 텍스트 패널 =====
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        if (world.state.cleared) {
            float titleY = cam.viewportHeight * 0.72f;
            float gap    = 32f;
            String line1 = "All stages cleared!";
            String line2 = "Press Enter to restart from Stage 1.";

            // 패널
            layout.setText(font, line1);
            float line1W = layout.width, line1H = layout.height;
            layout.setText(font, line2);
            float line2W = layout.width, line2H = layout.height;
            float panelW = Math.max(line1W, line2W) + 32f;
            float panelH = (line1H + line2H) + gap + 24f;
            float panelX = cam.position.x - panelW / 2f;
            float panelY = (titleY + 12f) - panelH;

            batch.end();
            Gdx.gl.glEnable(GL20.GL_BLEND);
            sr.setProjectionMatrix(cam.combined);
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(0f, 0f, 0f, 0.45f);
            sr.rect(panelX, panelY, panelW, panelH);
            sr.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
            batch.begin();

            drawCentered(line1, titleY);
            drawCentered(line2, titleY - gap);
        } else {
            font.draw(batch,
                (world.state.paused ? "[PAUSED] " : "") +
                    "Point: " + world.state.point +
                    "   Level: " + world.state.currentLevel +
                    "   [Z]double Jump! [X]Dash(cost : 1 point)  [R]Restart  [ESC/P]Pause",
                cam.position.x - 380, cam.viewportHeight - 12);
            if (shaking) {
                font.draw(batch, "Fell! Restarting...", cam.position.x - 80, cam.viewportHeight - 32);
            }
        }
        batch.end();
    }

    private void drawCentered(String text, float centerY) {
        layout.setText(font, text);
        float x = cam.position.x - layout.width / 2f;
        font.setColor(0,0,0,0.9f);
        font.draw(batch, layout, x + 1f, centerY - 1f);
        font.setColor(1,1,1,1);
        font.draw(batch, layout, x, centerY);
    }

    private void drawCrownedHeroLeft() {
        float leftEdge = cam.position.x - cam.viewportWidth / 2f;
        float baseX = leftEdge + 100f;
        float baseY = 120f;
        float w = 22f, h = 28f;

        sr.setColor(Color.SKY);
        sr.rect(baseX, baseY, w, h);

        Color crown = Color.GOLD;
        float crownW = w + 6f, crownH = 6f;
        float crownX = baseX - 3f;
        float crownY = baseY + h + 6f;

        sr.setColor(crown);
        sr.rect(crownX, crownY, crownW, crownH);

        float spikeH = 10f;
        float midX   = crownX + crownW / 2f;
        float topY   = crownY + crownH + spikeH;
        sr.triangle(crownX + 3f, crownY + crownH, (crownX + midX)/2f, topY, midX, crownY + crownH);
        sr.triangle(midX, crownY + crownH, (midX + crownX + crownW - 3f)/2f, topY, crownX + crownW - 3f, crownY + crownH);
        sr.triangle(crownX + crownW*0.33f, crownY + crownH, midX, topY + 4f, crownX + crownW*0.66f, crownY + crownH);

        sr.setColor(Color.SCARLET);
        sr.rect(midX - 2f, crownY + crownH + 2f, 4f, 6f);
    }

    // ===== GIF 유틸 =====
    private void loadClearGif(String internalPath) {
        try {
            InputStream in = Gdx.files.internal(internalPath).read();
            ImageInputStream iis = new MemoryCacheImageInputStream(in);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return;
            ImageReader reader = readers.next();
            reader.setInput(iis, false);

            clearGifTextures = new com.badlogic.gdx.utils.Array<>();
            clearGifRegions  = new com.badlogic.gdx.utils.Array<>();

            int num = reader.getNumImages(true);
            for (int i = 0; i < num; i++) {
                BufferedImage bi = reader.read(i);
                Texture tex = bufferedImageToTexture(bi);
                clearGifTextures.add(tex);
                clearGifRegions.add(new TextureRegion(tex));
            }
            reader.dispose();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            clearGifTextures = null;
            clearGifRegions = null;
        }
    }

    private TextureRegion getGifFrame(float elapsed) {
        if (clearGifRegions == null || clearGifRegions.size == 0) return null;
        int idx = (int)(elapsed / clearGifFrameDuration) % clearGifRegions.size;
        return clearGifRegions.get(idx);
    }

    private Texture bufferedImageToTexture(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        com.badlogic.gdx.graphics.Pixmap px = new com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        int[] data = new int[w*h];
        img.getRGB(0, 0, w, h, data, 0, w);
        int k = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = data[k++];
                int a = (p>>24)&0xff, r=(p>>16)&0xff, g=(p>>8)&0xff, b=(p)&0xff;
                px.drawPixel(x, h-1-y, Color.rgba8888(r/255f, g/255f, b/255f, a/255f));
            }
        }
        Texture t = new Texture(px);
        px.dispose();
        return t;
    }

    @Override public void dispose() {
        sr.dispose();
        batch.dispose();
        font.dispose();
        Assets.dispose();
        if (clearGifTextures != null) {
            for (Texture t : clearGifTextures) if (t != null) t.dispose();
        }
    }
}
