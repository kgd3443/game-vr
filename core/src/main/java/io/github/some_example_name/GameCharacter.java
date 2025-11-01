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

    // ===== 플랫포머 물리 파라미터 =====
    // 수평
    private float runAccel     = 2000f;  // 가속(px/s^2)
    private float runDecel     = 2500f;  // 감속(px/s^2)
    private float maxRunSpeed  = 450f;   // 최대 달리기 속도(px/s)
    private float friction     = 2000f;  // 지면 마찰(입력 없음)
    private float airControl   = 0.5f;   // 공중 제어 비율(0~1)

    // 수직
    private float gravity        = -2600f; // 중력(px/s^2)
    private float maxFallSpeed   = -1800f; // 최대 낙하 속도(음수)
    private float jumpVelocity   = 900f;   // 점프 초기 상승속도
    private float jumpCutFactor  = 0.5f;   // 점프 컷(키 떼면 상승속도 절반 등)

    // 점프 유틸
    private float COYOTE_TIME       = 0.12f; // 가장자리 유예
    private float JUMP_BUFFER_TIME  = 0.12f; // 입력 버퍼

    private float coyoteTimer = 0f;     // 남은 코요테 시간
    private float jumpBuffer  = 0f;     // 남은 버퍼 시간
    private boolean jumpCutQueued = false; // 키 뗐는지

    // 입력 상태
    private float moveAxis = 0f; // -1..+1

    public GameCharacter(Texture texture, float startX, float startY) {
        this.position.set(startX, startY);
        this.sprite = new Sprite(texture);
        this.sprite.setPosition(startX, startY);
    }

    // ===== 입력 API =====
    public void setMoveAxis(float axis) {
        // 축 입력 클램프
        if (axis > 1f) axis = 1f;
        if (axis < -1f) axis = -1f;
        this.moveAxis = axis;
    }

    public void onJumpPressed() {
        // 점프 입력 버퍼 시작
        this.jumpBuffer = JUMP_BUFFER_TIME;
    }

    public void onJumpReleased() {
        // 점프 컷 예약 (상승 중이면 감속)
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
            // 입력 방향으로 가속
            velocity.x += targetAccel * moveAxis * delta;
        } else if (isGrounded) {
            // 지면 마찰(입력 없을 때만)
            if (Math.abs(velocity.x) <= friction * delta) velocity.x = 0f;
            else velocity.x -= Math.signum(velocity.x) * friction * delta;
        } else {
            // 공중에서 입력 없음: 서서히 감속
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
        // 새로 착지했다면 코요테 타이머 리필
        if (grounded && !this.isGrounded) {
            coyoteTimer = COYOTE_TIME;
        }
        this.isGrounded = grounded;
    }

    // 월드가 호출: 착지/코요테/버퍼 조건을 만족하면 점프 실행
    public boolean tryConsumeJump() {
        boolean canJump = isGrounded || coyoteTimer > 0f;
        if (canJump && jumpBuffer > 0f) {
            // 점프 실행
            velocity.y = jumpVelocity;
            isGrounded = false;
            coyoteTimer = 0f;
            jumpBuffer = 0f;
            return true;
        }
        return false;
    }

    // 월드가 충돌 처리 후 호출: 점프 컷 적용(상승 중이고 컷 예약되었을 때)
    public void postCollisionUpdate() {
        if (jumpCutQueued && velocity.y > 0f) {
            velocity.y *= jumpCutFactor; // 상승속도 감소
        }
        jumpCutQueued = false;
    }

    public void jump() {
        // (호환: 즉시 점프 호출이 필요할 때)
        jumpBuffer = JUMP_BUFFER_TIME;
    }

    public void syncSpriteToPosition() {
        sprite.setPosition(position.x, position.y);
    }

    public void draw(SpriteBatch batch) {
        sprite.draw(batch);
    }
}
