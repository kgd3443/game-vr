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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.files.FileHandle;

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
    private GlyphLayout layout; // 중앙정렬/측정용

    private GameWorld world;

    // Shake state
    private boolean shaking = false;
    private float shakeTimer = 0f;

    // ====== 클리어 GIF 재생 관련 ======
    private Array<Texture> clearGifTextures;          // 로드한 프레임 텍스처들
    private Array<TextureRegion> clearGifRegions;     // 그리기용 리전
    private float clearGifFrameDuration = 0.06f;      // 대략 16~17fps
    private float clearGifTimer = 0f;                 // 경과 시간
    // =================================

    @Override
    public void create() {
        cam = new OrthographicCamera();
        cam.setToOrtho(false, Constants.V_WIDTH, Constants.V_HEIGHT);
        sr = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();   // 영어는 기본 BitmapFont 사용
        layout = new GlyphLayout();

        // 클리어 GIF 로드 (assets/clear.gif)
        loadClearGif("clear.gif"); // 파일은 core/assets/clear.gif 경로에 두세요

        world = new GameWorld();
    }

    private void handleInput(float dt) {
        // --- 클리어 화면: 엔터로 1스테이지부터 재시작 ---
        if (world.state.cleared) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                world.loadLevel(1);
                world.state.point = 0;
                clearGifTimer = 0f; // 타이머 리셋
            }
            return; // 클리어 상태에서는 다른 입력 무시
        }

        // Pause toggle (ESC or P)
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            world.state.paused = !world.state.paused;
        }
        // Restart level (R) - resets points
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            world.restartLevel(true);
        }
        if (world.state.paused || shaking) return; // 흔들림/일시정지 동안 이동 입력 차단

        // --- Move (no input while dashing) + 미끄럼 처리 ---
        float ax = 0f;
        boolean left  = Gdx.input.isKeyPressed(Input.Keys.LEFT)  || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);

        if (!world.player.dashing) {
            if (left)  ax -= Constants.MOVE_SPEED;
            if (right) ax += Constants.MOVE_SPEED;

            if (ax != 0f) {
                // 입력이 있으면 즉시 목표 속도로
                world.player.vel.x = ax;
            } else {
                // 입력이 없으면: 미끄럼 바닥에서만 서서히 감소, 아니면 즉시 정지
                if (world.onSlippery && world.player.grounded) {
                    world.player.vel.x *= Constants.SLIPPERY_DECAY; // 예: 0.985f
                    // 아주 작아지면 0으로 스냅
                    if (Math.abs(world.player.vel.x) < 1f) world.player.vel.x = 0f;
                } else {
                    world.player.vel.x = 0f;
                }
            }
        }

        // Jump (double jump)
        if (Gdx.input.isKeyJustPressed(Input.Keys.Z) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (world.player.jumpsLeft > 0) {
                world.player.vel.y = Constants.JUMP_VELOCITY;
                world.player.jumpsLeft--;
            }
        }

        // Dash (fixed distance: 5 tiles). Cost = 1 point
        if ((Gdx.input.isKeyJustPressed(Input.Keys.X) || Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT))
            && !world.player.dashing && world.state.point >= Constants.DASH_COST) {
            world.state.point -= Constants.DASH_COST;

            int dir = 1;
            if (left) dir = -1;
            else if (right) dir = 1;
            else if (world.player.vel.x < 0) dir = -1; // 입력 없으면 마지막 진행방향 유지

            world.player.startDash(dir);
        }

        // Dev hotkeys (테스트용)
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) world.loadLevel(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) world.loadLevel(2);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) world.loadLevel(3);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        // 낙사 감지 → 흔들림 시작 (클리어 상태가 아닐 때만)
        if (!world.state.cleared && world.fellThisFrame && !shaking) {
            shaking = true;
            shakeTimer = Constants.SHAKE_DURATION;
        }

        // 상태별 업데이트
        if (world.state.cleared) {
            handleInput(dt); // 엔터만 처리
            // 클리어 상태에서는 GIF만 시간 업데이트
            clearGifTimer += dt;
        } else if (!world.state.paused && !shaking) {
            handleInput(dt);
            world.step(dt);
        } else {
            handleInput(dt); // ESC/P, R 등은 받음
        }

        // camera follow
        float baseX = Math.max(cam.viewportWidth / 2f, world.player.pos.x + 100);
        float baseY = cam.viewportHeight / 2f;

        // apply shake
        float offsetX = 0f, offsetY = 0f;
        if (!world.state.cleared && shaking) {
            shakeTimer -= dt;
            float t = Math.max(0f, shakeTimer) / Constants.SHAKE_DURATION; // 1→0
            float amp = Constants.SHAKE_AMPLITUDE * t * t; // ease-out
            offsetX = MathUtils.random(-amp, amp);
            offsetY = MathUtils.random(-amp, amp);
            if (shakeTimer <= 0f) {
                shaking = false;
                world.restartLevel(true); // 낙사 후 포인트 0으로 재시작
            }
        }

        cam.position.x = baseX + offsetX;
        cam.position.y = baseY + offsetY;
        cam.update();

        Gdx.gl.glClearColor(0.1f, 0.12f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // ===== draw world or celebration layer =====
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        if (!world.state.cleared) {
            for (int i = 0; i < world.blocks.size; i++) {
                world.blocks.get(i).draw(sr);
            }
            world.player.draw(sr);
        } else {
            // 왼쪽에 메인 캐릭터 + 왕관 연출
            drawCrownedHeroLeft();
        }
        sr.end();

        // ===== UI + GIF (레이어 순서: GIF -> 반투명 패널 -> 텍스트) =====
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        if (world.state.cleared) {
            // 배치값: 텍스트를 위로, GIF와 간격 확대
            float titleY     = cam.viewportHeight * 0.72f; // 제목 라인 (상단 쪽)
            float gap        = 32f;                        // 두 줄 간격
            float gifCenterY = cam.viewportHeight * 0.56f; // GIF 중앙 높이 (화면 중앙보다 위)

            String line1 = "All stages cleared!";
            String line2 = "Press Enter to restart from Stage 1.";

            // 1) GIF 먼저
            TextureRegion frame = getGifFrame(clearGifTimer);
            float gifW = 0, gifH = 0, gifX = 0, gifY = 0;
            if (frame != null) {
                float scale = 1f; // 필요 시 0.8f ~ 1.2f 조정
                gifW = frame.getRegionWidth() * scale;
                gifH = frame.getRegionHeight() * scale;
                gifX = cam.position.x - gifW / 2f;
                gifY = gifCenterY - gifH / 2f;
                batch.draw(frame, gifX, gifY, gifW, gifH);
            }

            // 텍스트 레이아웃 측정
            layout.setText(font, line1);
            float line1W = layout.width;
            float line1H = layout.height;
            layout.setText(font, line2);
            float line2W = layout.width;
            float line2H = layout.height;

            float panelW = Math.max(line1W, line2W) + 32f;              // 좌우 패딩 16px
            float panelH = (line1H + line2H) + gap + 24f;               // 위아래 패딩 12px
            float panelX = cam.position.x - panelW / 2f;
            float panelTopY = titleY + 12f;                             // 패널 상단
            float panelY = panelTopY - panelH;                          // 패널 하단 Y

            // 2) 반투명 패널
            batch.end();
            Gdx.gl.glEnable(GL20.GL_BLEND);
            sr.setProjectionMatrix(cam.combined);
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(0f, 0f, 0f, 0.45f);
            sr.rect(panelX, panelY, panelW, panelH);
            sr.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
            batch.begin();

            // 3) 텍스트(항상 최상단, 중앙정렬 + 얇은 그림자)
            drawCentered(line1, titleY);
            drawCentered(line2, titleY - gap);
        } else {
            font.draw(batch,
                (world.state.paused ? "[PAUSED] " : "") +
                    "Point: " + world.state.point +
                    "   Level: " + world.state.currentLevel +
                    "   [Z]Jump x2  [X]Dash(cost : 1point)  [R]Restart  [ESC/P]Pause  [1~3]Level",
                cam.position.x - 380, cam.viewportHeight - 12);
            if (shaking) {
                font.draw(batch, "Fell! Restarting...", cam.position.x - 80, cam.viewportHeight - 32);
            }
        }
        batch.end();
    }

    private void drawCentered(String text, float centerY) {
        // 얇은 그림자 + 본문 (가독성 향상)
        layout.setText(font, text);
        float x = cam.position.x - layout.width / 2f;

        font.setColor(0, 0, 0, 0.9f);
        font.draw(batch, layout, x + 1f, centerY - 1f);

        font.setColor(1, 1, 1, 1);
        font.draw(batch, layout, x, centerY);
    }

    // --- 왼쪽에 메인 캐릭터 + 왕관 연출 (클리어 화면 전용) ---
    private void drawCrownedHeroLeft() {
        // 화면 왼쪽 여백 +100px 지점에 배치
        float leftEdge = cam.position.x - cam.viewportWidth / 2f;
        float baseX = leftEdge + 100f;

        // 바닥 기준선 살짝 위쪽
        float baseY = 120f;

        // 캐릭터 크기 (GameCharacter와 유사)
        float w = 22f, h = 28f;

        // 캐릭터 본체
        sr.setColor(Color.SKY);
        sr.rect(baseX, baseY, w, h);

        // 왕관 색
        Color crown = Color.GOLD;

        // 왕관 밑받침
        float crownW = w + 6f;    // 본체보다 약간 넓게
        float crownH = 6f;        // 두께
        float crownX = baseX - 3f;
        float crownY = baseY + h + 6f; // 머리 위 여유

        sr.setColor(crown);
        sr.rect(crownX, crownY, crownW, crownH);

        // 왕관 뾰족이(삼각형 3개)
        float spikeH = 10f;
        float leftX  = crownX + 3f;
        float midX   = crownX + crownW / 2f;
        float rightX = crownX + crownW - 3f;
        float topY   = crownY + crownH + spikeH;

        sr.triangle(leftX, crownY + crownH, (leftX + midX)/2f, topY, midX, crownY + crownH);
        sr.triangle(midX, crownY + crownH, (midX + rightX)/2f, topY, rightX, crownY + crownH);
        // 가운데를 조금 더 높게
        sr.triangle(crownX + crownW*0.33f, crownY + crownH, midX, topY + 4f, crownX + crownW*0.66f, crownY + crownH);

        // (선택) 왕관 보석
        sr.setColor(Color.SCARLET);
        float gemW = 4f, gemH = 6f;
        sr.rect(midX - gemW/2f, crownY + crownH + 2f, gemW, gemH);
    }

    // ====== GIF 로딩/재생 유틸 ======
    private void loadClearGif(String internalPath) {
        clearGifTextures = new Array<>();
        clearGifRegions  = new Array<>();
        try {
            FileHandle fh = Gdx.files.internal(internalPath);
            InputStream in = fh.read(); // assets에서 읽기
            ImageInputStream iis = new MemoryCacheImageInputStream(in);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                System.err.println("No GIF reader available.");
                return;
            }
            ImageReader reader = readers.next();
            reader.setInput(iis, false);

            int num = reader.getNumImages(true);
            for (int i = 0; i < num; i++) {
                BufferedImage bi = reader.read(i);
                Texture tex = bufferedImageToTexture(bi);
                clearGifTextures.add(tex);
                clearGifRegions.add(new TextureRegion(tex));
            }
            reader.dispose();
            in.close();

            if (clearGifRegions.size == 0) {
                clearGifTextures = null;
                clearGifRegions = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            clearGifTextures = null;
            clearGifRegions = null;
        }
    }

    private TextureRegion getGifFrame(float elapsed) {
        if (clearGifRegions == null || clearGifRegions.size == 0) return null;
        int frameIndex = (int)(elapsed / clearGifFrameDuration) % clearGifRegions.size;
        return clearGifRegions.get(frameIndex);
    }

    private Texture bufferedImageToTexture(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        com.badlogic.gdx.graphics.Pixmap px = new com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        int[] data = new int[w * h];
        img.getRGB(0, 0, w, h, data, 0, w);
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = data[idx++];
                int a = (p >> 24) & 0xff;
                int r = (p >> 16) & 0xff;
                int g = (p >> 8) & 0xff;
                int b = (p) & 0xff;
                px.drawPixel(x, h - 1 - y, Color.rgba8888(
                    r / 255f, g / 255f, b / 255f, a / 255f));
            }
        }
        Texture t = new Texture(px);
        px.dispose();
        return t;
    }
    // ================================

    @Override public void dispose() {
        sr.dispose();
        batch.dispose();
        font.dispose();
        if (clearGifTextures != null) {
            for (Texture t : clearGifTextures) if (t != null) t.dispose();
        }
    }
}
