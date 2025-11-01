package io.github.some_example_name;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;

public class GameCharacter {
    public Vector2 position = new Vector2();
    public Vector2 velocity = new Vector2();
    public Sprite sprite;
    public boolean isGrounded = false;

    // ===== 플랫포머 물리 파라미터 (VIEW_H=208px 스케일에 맞춤) =====
    // 수평
    private float runAccel     = 900f;   // px/s^2
    private float runDecel     = 1100f;  // px/s^2
    private float maxRunSpeed  = 120f;   // px/s (약 7.5타일/s)
    private float friction     = 900f;   // 지면 마찰
    private float airControl   = 0.6f;   // 공중 제어 비율(0~1)

    // 수직
    private float gravity        = -1200f; // px/s^2
    private float maxFallSpeed   = -900f;  // px/s
    private float jumpVelocity   = 300f;   // px/s
    private float jumpCutFactor  = 0.5f;   // 점프 컷(키 떼면 상승속도 감소)

    // 점프 유틸
    private float COYOTE_TIME       = 0.12f; // 가장자리 유예
    private float JUMP_BUFFER_TIME  = 0.12f; // 입력 버퍼

    private float coyoteTimer = 0f;     // 남은 코요테 시간
    private float jumpBuffer  = 0f;     // 남은 버퍼 시간
    private boolean jumpCutQueued = false; // 점프키 떼짐 예약

    // 이중 점프
    private int maxJumps = 2;     // 총 점프 가능 횟수(지상 기준)
    private int jumpsLeft = 2;    // 남은 점프 횟수

    // 입력 상태
    private float moveAxis = 0f; // -1..+1

    public GameCharacter(Texture texture, float startX, float startY) {
        this.position.set(startX, startY);
        this.sprite = new Sprite(texture);
        this.sprite.setPosition(startX, startY);
    }

    // ===== 입력 API =====
    public void setMoveAxis(float axis) {
        if (axis > 1f) axis = 1f;
        if (axis < -1f) axis = -1f;
        this.moveAxis = axis;
    }

    public void onJumpPressed() {
        this.jumpBuffer = JUMP_BUFFER_TIME;
    }

    public void onJumpReleased() {
        this.jumpCutQueued = true;
    }

    // ===== 타이머/물리 갱신(충돌 계산 전) =====
    public void preUpdate(float delta) {
        // 타이머 감소
        if (coyoteTimer > 0f) coyoteTimer -= delta;
        if (jumpBuffer  > 0f) jumpBuffer  -= delta;

        // 수평 가감속
        float targetAccel = (isGrounded ? 1f : airControl) * (moveAxis != 0 ? runAccel : runDecel);
        if (moveAxis != 0f) {
            velocity.x += targetAccel * moveAxis * delta;
        } else if (isGrounded) {
            // 지면 마찰
            if (Math.abs(velocity.x) <= friction * delta) velocity.x = 0f;
            else velocity.x -= Math.signum(velocity.x) * friction * delta;
        } else {
            // 공중 감속
            float airDecel = runDecel * airControl;
            if (Math.abs(velocity.x) <= airDecel * delta) velocity.x = 0f;
            else velocity.x -= Math.signum(velocity.x) * airDecel * delta;
        }

        // 최대 속도 제한
        if (velocity.x >  maxRunSpeed) velocity.x =  maxRunSpeed;
        if (velocity.x < -maxRunSpeed) velocity.x = -maxRunSpeed;

        // 중력 적용 + 최대 낙하속도 제한
        velocity.y += gravity * delta;
        if (velocity.y < maxFallSpeed) velocity.y = maxFallSpeed;
    }

    // 바닥 충돌 결과를 월드가 알려줌
    public void setGrounded(boolean grounded) {
        if (grounded && !this.isGrounded) {
            coyoteTimer = COYOTE_TIME; // 새 착지 시 타이머 리필
            jumpsLeft = maxJumps;       // 착지 시 점프 횟수 리셋
        }
        this.isGrounded = grounded;
    }

    // 월드가 호출: 버퍼+남은 점프를 확인해 점프 실행
    public boolean tryConsumeJump() {
        // 남은 점프가 있어야 하며, 버퍼 안에 입력이 들어왔을 때 점프 실행
        // 코요테가 남아있으면 '첫 점프'를 지상 판정처럼 허용
        boolean canJumpNow = (jumpsLeft > 0) && (jumpBuffer > 0f) && (isGrounded || coyoteTimer > 0f || !isGrounded);
        if (canJumpNow) {
            velocity.y = jumpVelocity;
            isGrounded = false;
            coyoteTimer = 0f;
            jumpBuffer = 0f;
            jumpsLeft--;         // 점프 1회 소모
            return true;
        }
        return false;
    }

    // 충돌 처리 후: 점프 컷(상승 중이고 컷 예약되었을 때)
    public void postCollisionUpdate() {
        if (jumpCutQueued && velocity.y > 0f) {
            velocity.y *= jumpCutFactor;
        }
        jumpCutQueued = false;
    }

    public void syncSpriteToPosition() {
        sprite.setPosition(position.x, position.y);
    }

    public void draw(SpriteBatch batch) {
        sprite.draw(batch);
    }
}
