package io.github.some_example_name;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;

public class Block {
    public enum Type { SOLID, BREAKABLE, GOAL } // GOAL = 하얀색, 닿으면 다음 스테이지

    public final int gx, gy;     // grid coords
    public final Type type;

    public Block(int gx, int gy, Type type) {
        this.gx = gx;
        this.gy = gy;
        this.type = type;
    }

    public Rectangle getBounds() {
        return new Rectangle(gx * Constants.TILE, gy * Constants.TILE, Constants.TILE, Constants.TILE);
    }

    public void draw(ShapeRenderer sr) {
        Rectangle r = getBounds();
        if (type == Type.SOLID) sr.setColor(Color.DARK_GRAY);
        else if (type == Type.BREAKABLE) sr.setColor(Color.GOLD);
        else sr.setColor(Color.WHITE); // GOAL
        sr.rect(r.x, r.y, r.width, r.height);
    }
}
