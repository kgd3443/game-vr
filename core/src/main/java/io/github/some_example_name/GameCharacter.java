package io.github.some_example_name;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class GameCharacter {
    public final Vector2 pos = new Vector2();
    public final Vector2 vel = new Vector2();
    public final float w = 22, h = 28;

    public boolean grounded = false;

    // Jump
    public int jumpsLeft = Constants.MAX_JUMPS;

    // Dash
    public boolean dashing = false;
    public float dashRemaining = 0f; // 남은 거리(px)
    public int dashDir = 1;          // -1 or +1

    public GameCharacter(float x, float y) {
        pos.set(x, y);
    }

    public Rectangle getBounds() {
        return new Rectangle(pos.x, pos.y, w, h);
    }

    public void startDash(int direction) {
        dashing = true;
        dashDir = Math.signum(direction) == -1 ? -1 : 1;
        dashRemaining = Constants.DASH_DISTANCE;
        vel.x = dashDir * Constants.DASH_SPEED;
    }

    public void stopDash() {
        dashing = false;
        vel.x = 0f;
    }

    public void draw(ShapeRenderer sr) {
        sr.setColor(dashing ? Color.SCARLET : (grounded ? Color.SKY : Color.CYAN));
        sr.rect(pos.x, pos.y, w, h);
    }
}
