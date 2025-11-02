package io.github.some_example_name;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;

public class Block {
    public enum Type {
        SOLID,           // 회색: 일반 벽
        BREAKABLE,       // 금색: 헤딩/대시로 파괴
        GOAL,            // 흰색: 다음 스테이지 (W)
        SLIPPERY,        // 연회색: 미끄럼 (S)
        POISON,          // 보라(정지형): 닿으면 현재 스테이지 처음으로 (P, R도 여기로 흡수)
        POISON_MOVING    // 보라(이동형): 좌우 왕복 (r도 여기로 흡수)
    }

    public final int gx, gy;
    public final Type type;

    // 이동 블록용
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
            vx = Constants.PURPLE_SPEED; // 기존 상수 재사용
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

    public void draw(ShapeRenderer sr) {
        Rectangle r = getBounds();
        switch (type) {
            case SOLID:
                sr.setColor(Color.DARK_GRAY); break;
            case BREAKABLE:
                sr.setColor(Color.GOLD); break;
            case GOAL:
                sr.setColor(Color.WHITE); break;
            case SLIPPERY:
                sr.setColor(new Color(0.94f, 0.94f, 0.94f, 1f)); break; // 거의 흰색
            case POISON:
            case POISON_MOVING:
                sr.setColor(new Color(0.60f, 0.25f, 0.85f, 1f)); break; // 보라
        }
        sr.rect(r.x, r.y, r.width, r.height);
    }
}
