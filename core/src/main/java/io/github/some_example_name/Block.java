package io.github.some_example_name;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;

public class Block {
    public enum Type {
        SOLID,            // #
        BREAKABLE,        // B
        GOAL,             // W
        SLIPPERY,         // S
        POISON,           // R (정지형 독)
        POISON_MOVING     // r (이동형 독)
    }

    public final int gx, gy;
    public final Type type;

    // 이동 블록
    public boolean moving = false;
    public float px, py;
    public float vx = 0f;
    public float minX, maxX;

    public Block(int gx, int gy, Type type) {
        this.gx = gx;
        this.gy = gy;
        this.type = type;

        this.px = gx * Constants.TILE;
        this.py = gy * Constants.TILE;

        if (type == Type.POISON_MOVING) {
            moving = true;
            vx = Constants.PURPLE_SPEED;
            float range = Constants.PURPLE_RANGE_TILES * Constants.TILE;
            minX = px - range * 0.5f;
            maxX = px + range * 0.5f;
        }
    }

    public Rectangle getBounds() {
        if (moving) return new Rectangle(px, py, Constants.TILE, Constants.TILE);
        return new Rectangle(gx * Constants.TILE, gy * Constants.TILE, Constants.TILE, Constants.TILE);
    }

    public void update(float dt) {
        if (!moving) return;
        px += vx * dt;
        if (px < minX) { px = minX; vx = Math.abs(vx); }
        if (px + Constants.TILE > maxX) { px = maxX - Constants.TILE; vx = -Math.abs(vx); }
    }

    // 텍스처 드로우(있으면 사용)
    public void draw(SpriteBatch batch) {
        Rectangle r = getBounds();
        Texture tex = null;
        switch (type) {
            case SOLID:           tex = Assets.TEX_SOLID; break;
            case BREAKABLE:       tex = Assets.TEX_BREAKABLE; break;
            case GOAL:            tex = Assets.TEX_GOAL; break;
            case POISON:          tex = Assets.TEX_POISON; break;
            case POISON_MOVING:   tex = Assets.TEX_POISON_MOVING; break;
            case SLIPPERY:        tex = Assets.TEX_SLIPPERY; break;
        }
        if (tex != null) {
            batch.draw(tex, r.x, r.y, r.width, r.height);
        } else {
            if (type == Type.SLIPPERY) {
            }
        }
    }

    public void drawShape(ShapeRenderer sr) {
        if (type != Type.SLIPPERY || Assets.TEX_SLIPPERY != null) return;
        Rectangle r = getBounds();
        sr.setColor(new Color(0.94f, 0.94f, 0.94f, 1f));
        sr.rect(r.x, r.y, r.width, r.height);
    }
}
