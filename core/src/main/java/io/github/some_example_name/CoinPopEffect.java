package io.github.some_example_name;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Interpolation;

public class CoinPopEffect {
    private final Sprite sprite;
    private float x, y;
    private float elapsed = 0f;
    private final float duration = 0.45f;   // 전체 재생 시간
    private final float rise = 28f;         // 위로 튀는 거리(px)
    public boolean alive = true;

    public CoinPopEffect(Texture coinTexture, float startCenterX, float startTopY) {
        this.sprite = new Sprite(coinTexture);
        // 코인 크기(타일 1칸)로 맞추기
        this.sprite.setSize(16f, 16f);
        // 블록 중앙에서 살짝 위로 시작
        this.x = startCenterX - sprite.getWidth()/2f;
        this.y = startTopY + 2f;
        this.sprite.setPosition(this.x, this.y);
    }

    /** dt만큼 업데이트. 끝나면 alive=false */
    public void update(float dt) {
        if (!alive) return;
        elapsed += dt;
        float t = Math.min(elapsed / duration, 1f);

        // 위로 튀었다가 살짝 내려오는 이징
        float offset = Interpolation.sineOut.apply(Math.min(t * 1.2f, 1f)) * rise;
        sprite.setPosition(x, y + offset);

        // 페이드 아웃(후반부)
        float alpha = 1f - Interpolation.fade.apply(Math.max(0f, (t - 0.5f) / 0.5f));
        sprite.setAlpha(alpha);

        if (t >= 1f) alive = false;
    }

    public void draw(SpriteBatch batch) {
        if (alive) sprite.draw(batch);
    }
}
