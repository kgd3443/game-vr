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
    public static final int DASH_COST = 1;

    // Points
    public static final int BREAK_POINT = 1;

    // Screen
    public static final int V_WIDTH = 800;
    public static final int V_HEIGHT = 480;

    // Shake (on falling into pit)
    public static final float SHAKE_DURATION = 0.5f; // seconds
    public static final float SHAKE_AMPLITUDE = 12f; // pixels
}
