package io.github.some_example_name;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;

public class GameCharacter {
    public Vector2 position;
    public Vector2 velocity;
    public Sprite sprite;
    public boolean isGrounded;

    // 프레임 독립 이동 속도
    private float moveSpeed = 400f; // px/sec

    public GameCharacter(Texture texture, float startX, float startY) {
        this.position = new Vector2(startX, startY);
        this.velocity = new Vector2(0, 0);
        this.sprite = new Sprite(texture);
        this.sprite.setPosition(startX, startY);
        this.isGrounded = false;
    }

    public void jump() {
        if (isGrounded) {
            velocity.y = 1600f;
            isGrounded = false;
        }
    }

    // 프레임 독립 좌우 이동
    public void moveRight(float delta) {
        position.x += moveSpeed * delta;
    }
    public void moveLeft(float delta) {
        position.x -= moveSpeed * delta;
    }

    // (호환용) 기존 메서드 - 사용 비권장
    @Deprecated public void moveRight() { position.x += 14; }
    @Deprecated public void moveLeft()  { position.x -= 14; }

    public void syncSpriteToPosition() {
        sprite.setPosition(position.x, position.y);
    }

    public void draw(SpriteBatch batch) {
        sprite.draw(batch);
    }
}
