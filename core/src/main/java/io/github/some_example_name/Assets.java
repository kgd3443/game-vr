package io.github.some_example_name;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;

public class Assets {
    public static Texture TEX_SOLID;
    public static Texture TEX_BREAKABLE;
    public static Texture TEX_GOAL;
    public static Texture TEX_POISON;          // R
    public static Texture TEX_POISON_MOVING;   // r
    public static Texture TEX_SLIPPERY;        // S (없으면 null)

    public static void load() {
        TEX_SOLID         = new Texture(Gdx.files.internal("solid.png"));
        TEX_BREAKABLE     = new Texture(Gdx.files.internal("breakable.png"));
        TEX_GOAL          = new Texture(Gdx.files.internal("goal.png"));
        TEX_POISON        = new Texture(Gdx.files.internal("poison.png"));
        TEX_POISON_MOVING = new Texture(Gdx.files.internal("poison_moving.png"));

        // 선택: 미끄럼 이미지가 있으면 사용
        if (Gdx.files.internal("slippery.png").exists()) {
            TEX_SLIPPERY = new Texture(Gdx.files.internal("slippery.png"));
        } else {
            TEX_SLIPPERY = null;
        }

        // 픽셀아트 선명도 유지
        Texture[] arr = {TEX_SOLID, TEX_BREAKABLE, TEX_GOAL, TEX_POISON, TEX_POISON_MOVING, TEX_SLIPPERY};
        for (Texture t : arr) {
            if (t != null) t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        }
    }

    public static void dispose() {
        if (TEX_SOLID != null) TEX_SOLID.dispose();
        if (TEX_BREAKABLE != null) TEX_BREAKABLE.dispose();
        if (TEX_GOAL != null) TEX_GOAL.dispose();
        if (TEX_POISON != null) TEX_POISON.dispose();
        if (TEX_POISON_MOVING != null) TEX_POISON_MOVING.dispose();
        if (TEX_SLIPPERY != null) TEX_SLIPPERY.dispose();
    }
}
