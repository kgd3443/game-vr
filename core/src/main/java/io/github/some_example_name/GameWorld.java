package io.github.some_example_name;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;

public class GameWorld {
    public final Array<Block> blocks = new Array<>();
    public final GameCharacter player;
    public final GameState state = new GameState();

    public int widthTiles;
    public int heightTiles;

    public boolean fellThisFrame = false;
    public boolean onSlippery = false;

    private static final float WALL_SLIDE_MAX_FALL_SPEED = -120f;

    // 프레임 동안 옆면에 닿았는지 표시
    private boolean touchingWallThisFrame = false;

    public GameWorld() {
        player = new GameCharacter(64, 96);
        loadLevel(1);
    }

    public void restartLevel(boolean resetPoint) {
        int lv = state.currentLevel;
        loadLevel(lv);
        if (resetPoint) state.point = 0;
    }

    public void nextLevel() {
        if (state.currentLevel >= 3) {
            completeGame();
            return;
        }
        int next = state.currentLevel + 1;
        state.point = 0;
        loadLevel(next);
    }

    public void completeGame() {
        state.cleared = true;
        state.point = 0;
        blocks.clear();
        player.pos.set(64, 5 * Constants.TILE);
        player.vel.set(0, 0);
        player.grounded = true;
        player.jumpsLeft = Constants.MAX_JUMPS;
        player.stopDash();
        onSlippery = false;
    }

    public void loadLevel(int lv) {
        state.cleared = false;
        blocks.clear();
        state.currentLevel = MathUtils.clamp(lv, 1, 3);
        fellThisFrame = false;
        onSlippery = false;

        String[] rows;
        switch (state.currentLevel) {
            case 1: rows = makeLevel1(); break;
            case 2: rows = makeLevel2(); break;
            default: rows = makeLevel3(); break;
        }
        heightTiles = rows.length;
        widthTiles  = rows[0].length();

        for (int y=0; y<rows.length; y++) {
            String row = rows[y];
            for (int x=0; x<row.length(); x++) {
                char c = row.charAt(x);
                int gy = rows.length-1-y;
                if (c == '#') blocks.add(new Block(x, gy, Block.Type.SOLID));
                if (c == 'B') blocks.add(new Block(x, gy, Block.Type.BREAKABLE));
                if (c == 'W') blocks.add(new Block(x, gy, Block.Type.GOAL));
                if (c == 'S') blocks.add(new Block(x, gy, Block.Type.SLIPPERY));
                if (c == 'R') blocks.add(new Block(x, gy, Block.Type.POISON));           // 보라(정지)
                if (c == 'r') blocks.add(new Block(x, gy, Block.Type.POISON_MOVING));    // 보라(이동)
            }
        }

        // 스폰
        player.pos.set(64, 5 * Constants.TILE);
        player.vel.set(0, 0);
        player.grounded = false;
        player.jumpsLeft = Constants.MAX_JUMPS;
        player.stopDash();

        ensureSafeSpawn();
    }

    // ===== 사용자가 제공한 맵 =====
    private String[] makeLevel1() {
        return new String[] {
            ".................................................................................................",
            "######################################...........................................................",
            ".....#..................#...R.....R..#...........................................................",
            ".....#..................#..R.R..RRRRR#.................................................#.......#.",
            ".....#...............#..#.RRRRR...R..#............................B....................#.......#",
            ".....#########.......#..#R.....R..R..#.................................................#.......#.",
            ".............#......##..##############.........B.......................................#.......#",
            "....B...............##.............B.#.........................#####.....#......#......#....W..#",
            "...................###...............B.........................#####...................#.......#",
            "#####.........########...#######################...............#####...................#########"
        };
    }

    private String[] makeLevel2() {
        return new String[] {
            "................RRRRRRRRRRRR....................................................................",
            ".................RRRR....RRR.............RRR...RR...............................................",
            "....................R...........RRR..##..RRR...RR................................................",
            "...............B......B......B..RRR......RRR...RR.....B.........................................",
            ".........................RRR....RRR......RRR...RRRRRRR.RRRRRRRRR........B...RRRRRR##RRRRRR..RRR#",
            ".................RRR.....RRR....RRRRRRRR.RRR..................RRRR.........RRRRRRRRRRRRRRR..RRR#",
            "B.....#R......R#########################################..................RRRRRRRRRRRRRRRR..RRR#",
            "......RR......RRRRRRRRRRRRRRRRRRRRRRRRRRRRRR...........########.....#####RRRBBBBBBBBBBBBBB..W..#",
            "......RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR##................RRR##.....########################",
            "######RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR................................................"
        };
    }

    private String[] makeLevel3() {
        return new String[] {
            "...............................................................................................#",
            "...............................................................................................#",
            "....RRRRRRRRRRR................................................................................#",
            "RRRRR.........RRRRR.......................r.....B.RRRRR............r..........R................#",
            "...........................................................RR.......r.........R................#",
            "...........................r......r.........RR.SSSS........r.........r.....R..R................#",
            "B........R....r....B........r...B...........RR...........SSSSSS.......r....R..R...r.....R......#",
            "........RRR........r.......r..........r.....RR.............................R..R.......r......W.#",
            ".......RRRRR................r.....RRRRRRR###..........................SSSSSS............SSSSSSS#",
            "SSSSSSSSSSSSSSSSSSSSSSSS.......SSS.........................................SSSSSSSSSSSSSS......."
        };
    }
    // ==========================

    public void step(float dt) {
        if (state.cleared) return;

        fellThisFrame = false;
        onSlippery = false;
        touchingWallThisFrame = false;

        for (int i = 0; i < blocks.size; i++) {
            Block b = blocks.get(i);
            if (b.moving) b.update(dt);
        }

        // 중력
        player.vel.y += Constants.GRAVITY * dt;

        // 예측
        Rectangle pb = player.getBounds();
        float newX = pb.x + player.vel.x * dt;
        float newY = pb.y + player.vel.y * dt;

        // X 충돌(옆면 접촉 판정 포함)
        Rectangle nx = new Rectangle(newX, pb.y, pb.width, pb.height);
        float movedX = resolveX(nx);
        if (player.dashing) {
            player.dashRemaining -= Math.abs(movedX);
            if (player.dashRemaining <= 0f) player.stopDash();
            else player.vel.x = player.dashDir * Constants.DASH_SPEED;
        }

        // Y 충돌
        Rectangle ny = new Rectangle(nx.x, newY, nx.width, nx.height);
        resolveY(ny, player.vel.y > 0);

        player.pos.set(ny.x, ny.y);
        player.grounded = isStandingOnBlock(player.getBounds());
        if (player.grounded) {
            player.jumpsLeft = Constants.MAX_JUMPS;
        }

        if (!player.grounded && touchingWallThisFrame && player.vel.y < WALL_SLIDE_MAX_FALL_SPEED) {
            player.vel.y = WALL_SLIDE_MAX_FALL_SPEED;
        }

        checkTriggers(player.getBounds());

        if (player.pos.y < -128f) {
            fellThisFrame = true;
        }
    }

    public void draw(SpriteBatch batch) {
        for (int i = 0; i < blocks.size; i++) {
            blocks.get(i).draw(batch);
        }
    }

    private float resolveX(Rectangle r) {
        float before = player.pos.x;
        for (int i = blocks.size-1; i >= 0; i--) {
            Block b = blocks.get(i);
            Rectangle br = b.getBounds();
            if (!r.overlaps(br)) continue;

            if (isTriggerBlock(b.type)) continue;

            // 대시 중 파괴
            if (player.dashing && b.type == Block.Type.BREAKABLE) {
                blocks.removeIndex(i);
                state.point += Constants.BREAK_POINT;
                continue;
            }

            if (player.vel.x > 0) r.x = br.x - r.width - 0.01f;
            else if (player.vel.x < 0) r.x = br.x + br.width + 0.01f;
            player.vel.x = 0;
            touchingWallThisFrame = true;
        }
        return r.x - before;
    }

    // Y 충돌: 트리거(POISON/GOAL)는 통과
    private void resolveY(Rectangle r, boolean movingUp) {
        for (int i = blocks.size-1; i >= 0; i--) {
            Block b = blocks.get(i);
            Rectangle br = b.getBounds();
            if (!r.overlaps(br)) continue;

            if (isTriggerBlock(b.type)) continue;

            if (movingUp && r.y + r.height > br.y && player.vel.y > 0) {
                if (b.type == Block.Type.BREAKABLE) {
                    blocks.removeIndex(i);
                    state.point += Constants.BREAK_POINT;
                }
                r.y = br.y - r.height - 0.01f;
                player.vel.y = 0;
            } else {
                if (player.vel.y < 0) {
                    r.y = br.y + br.height + 0.01f;
                    player.vel.y = 0;
                    if (b.type == Block.Type.SLIPPERY) onSlippery = true;
                    if (b.moving) r.x += b.vx * Constants.MOVING_CARRY_RATIO;
                } else if (player.vel.y > 0) {
                    r.y = br.y - r.height - 0.01f;
                    player.vel.y = 0;
                }
            }
        }
    }

    private boolean isStandingOnBlock(Rectangle r) {
        Rectangle below = new Rectangle(r.x, r.y - 2, r.width, r.height);
        for (Block b : blocks) {
            if (isTriggerBlock(b.type)) continue;
            if (below.overlaps(b.getBounds())) return true;
        }
        return false;
    }

    private void checkTriggers(Rectangle r) {
        boolean hitPoison = false;
        boolean hitGoal   = false;

        for (int i = 0; i < blocks.size; i++) {
            Block b = blocks.get(i);
            if (!r.overlaps(b.getBounds())) continue;

            if (b.type == Block.Type.POISON || b.type == Block.Type.POISON_MOVING) hitPoison = true;
            else if (b.type == Block.Type.GOAL) hitGoal = true;
        }

        if (hitPoison) {
            // 흔들림 → Main이 타이머 종료 시 restartLevel(true) 호출
            fellThisFrame = true;
            return;
        }
        if (hitGoal) {
            nextLevel();
        }
    }

    private boolean isTriggerBlock(Block.Type t) {
        return t == Block.Type.GOAL || t == Block.Type.POISON || t == Block.Type.POISON_MOVING;
    }

    // 스폰 시 트리거와 겹치면 주변 안전 타일로 이동
    private void ensureSafeSpawn() {
        Rectangle pr = player.getBounds();

        for (int k = 0; k < 12; k++) {
            if (!overlapsAnyTrigger(pr)) return;
            player.pos.y += Constants.TILE;
            pr.setPosition(player.pos.x, player.pos.y);
        }

        int[] dxs = { -3, -2, -1, 1, 2, 3 };
        int[] dys = { 0, 1, 2, -1, -2, 3, 4 };
        for (int dx : dxs) {
            for (int dy : dys) {
                float nx = 64 + dx * Constants.TILE;
                float ny = 5 * Constants.TILE + dy * Constants.TILE;
                pr.setPosition(nx, ny);
                if (!overlapsAnyTrigger(pr)) { player.pos.set(nx, ny); return; }
            }
        }
        player.pos.x -= Constants.TILE; // 최후 수단
    }

    private boolean overlapsAnyTrigger(Rectangle rr) {
        for (int i = 0; i < blocks.size; i++) {
            Block b = blocks.get(i);
            if (!isTriggerBlock(b.type)) continue;
            if (rr.overlaps(b.getBounds())) return true;
        }
        return false;
    }
}
