package io.github.some_example_name;

public final class Constants {
    private Constants() {}

    public static final int TILE = 32;

    // Physics
    public static final float GRAVITY = -1300f;
    public static final float MOVE_SPEED = 200f;
    public static final float JUMP_VELOCITY = 520f;

    // Double Jump
    public static final int MAX_JUMPS = 2; // 2단 점프

    // Dash (fixed distance: 5 tiles)
    public static final int DASH_TILES = 5;
    public static final float DASH_DISTANCE = DASH_TILES * TILE; // 5칸
    public static final float DASH_SPEED = 900f;
    public static final int DASH_COST = 1;   // 스킬 비용 1 포인트

    // Points
    public static final int BREAK_POINT = 1; // 블록 파괴 시 +1

    // Screen
    public static final int V_WIDTH = 800;
    public static final int V_HEIGHT = 480;

    // Shake (on falling into pit)
    public static final float SHAKE_DURATION = 0.5f; // seconds
    public static final float SHAKE_AMPLITUDE = 12f; // pixels
    // Moving purple (왕복)
    public static final float PURPLE_SPEED = 80f;              // px/s
    public static final int   PURPLE_RANGE_TILES = 6;          // 왕복 범위

    // Slippery
    public static final float SLIPPERY_DECAY = 0.985f;         // 미끄럼 감쇠(1에 가까울수록 오래 미끄러짐)

    // 이동 블록 위 운반 비율
    public static final float MOVING_CARRY_RATIO = 0.06f;      // b.vx 일부를 플레이어에 전달

}
