package io.github.some_example_name;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class GameWorld {
    public final Array<Block> blocks = new Array<>();
    public final GameCharacter player;
    public final GameState state = new GameState();

    public int widthTiles;
    public int heightTiles;

    // Event flags
    public boolean fellThisFrame = false;

    public GameWorld() {
        player = new GameCharacter(64, 96);
        loadLevel(1);
    }

    public void restartLevel(boolean resetPoint) {
        int lv = state.currentLevel;
        loadLevel(lv);
        if (resetPoint) state.point = 0;  // 포인트 리셋 옵션 (R키/낙사 등에서 true로 호출)
    }

    // ✅ 다음 스테이지 로직: 3스테이지 → 클리어 화면
    public void nextLevel() {
        if (state.currentLevel >= 3) {
            completeGame();       // 3스테이지 골에 닿으면 클리어 상태로 전환
            return;
        }
        int next = state.currentLevel + 1;
        state.point = 0;          // 스테이지 이동 시 포인트 리셋
        loadLevel(next);
    }

    // ✅ 게임 클리어 처리
    public void completeGame() {
        state.cleared = true;     // 클리어 화면 진입
        state.point = 0;          // 클리어 시 포인트도 0으로
        blocks.clear();           // 화면 정리(선택)
        // 플레이어 위치/속도 초기화
        player.pos.set(64, 5 * Constants.TILE);
        player.vel.set(0, 0);
        player.grounded = true;
        player.jumpsLeft = Constants.MAX_JUMPS;
        player.stopDash();
    }

    public void loadLevel(int lv) {
        state.cleared = false;    // 스테이지 로드 시 클리어 상태 해제
        blocks.clear();
        state.currentLevel = MathUtils.clamp(lv, 1, 3);
        fellThisFrame = false;

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
                if (c == 'W') blocks.add(new Block(x, gy, Block.Type.GOAL)); // White - goal
            }
        }

        // reset player
        player.pos.set(64, 5 * Constants.TILE);
        player.vel.set(0, 0);
        player.grounded = false;
        player.jumpsLeft = Constants.MAX_JUMPS;
        player.stopDash();
    }

    private String[] makeLevel1() {
        return new String[] {
            "................................................................................................",
            "...............................................B...B............................................",
            "..............................B....................................B.............................",
            "......................BBB......................................................B.................",
            "................................................................................................",
            "..............B......................BBB.................................B.......................",
            "................................................................................................",
            ".............................B..............................................................W...",
            "................................................................................................",
            "#########..###########..###########..###########..###########..##############..#########..######"
        };
    }

    private String[] makeLevel2() {
        return new String[] {
            "................................................................................................",
            "...............BBB...............B..B..B........................................................",
            "................................................................................................",
            "...........B..................BBB.........................B.....................................",
            "................................................................................................",
            "....................BBB.........................................................BBB.............",
            "................................................................................................",
            "..................................................B.........................................W...",
            "................................................................................................",
            "#########..#####..#####..#####..#####..#####..#####..###########..#########..#########..########"
        };
    }

    private String[] makeLevel3() {
        return new String[] {
            "................................................................................................",
            "...................B..B..B..B..B..B..B..........................................................",
            "................................................................................................",
            ".........B.....................BBB....................B.........................................",
            "................................................................................................",
            "..........................B......................B..............................................",
            "................................................................................................",
            "..............................................................B.............................W...",
            "................................................................................................",
            "#########..#########..#########..#########..#########..#########..#########..#########..########"
        };
    }

    // --- physics
    public void step(float dt) {
        if (state.cleared) return;

        fellThisFrame = false;

        // gravity
        player.vel.y += Constants.GRAVITY * dt;

        // predict
        Rectangle pb = player.getBounds();
        float newX = pb.x + player.vel.x * dt;
        float newY = pb.y + player.vel.y * dt;

        // X
        Rectangle nx = new Rectangle(newX, pb.y, pb.width, pb.height);
        float movedX = resolveX(nx);
        if (player.dashing) {
            player.dashRemaining -= Math.abs(movedX);
            if (player.dashRemaining <= 0f) {
                player.stopDash();
            } else {
                player.vel.x = player.dashDir * Constants.DASH_SPEED; // 대시 중 속도 유지
            }
        }

        // Y (heading detection). Do NOT destroy on Y.
        Rectangle ny = new Rectangle(nx.x, newY, nx.width, nx.height);
        resolveY(ny, player.vel.y > 0);

        // commit
        player.pos.set(ny.x, ny.y);
        player.grounded = isStandingOnBlock(player.getBounds());
        if (player.grounded) {
            player.jumpsLeft = Constants.MAX_JUMPS; // 착지 시 점프 회복
        }

        // goal check
        checkGoal(player.getBounds());

        // pitfall check
        if (player.pos.y < -128f) {
            fellThisFrame = true;
        }
    }

    private float resolveX(Rectangle r) {
        float before = player.pos.x;
        for (int i = blocks.size-1; i >= 0; i--) {
            Block b = blocks.get(i);
            Rectangle br = b.getBounds();
            if (r.overlaps(br)) {
                if (b.type == Block.Type.GOAL) continue; // GOAL은 통과
                if (player.dashing) {
                    // 대시 중 X축에서 부딪히는 블럭 파괴 (GOAL 제외)
                    blocks.removeIndex(i);
                    state.point += Constants.BREAK_POINT;
                    continue;
                }
                if (player.vel.x > 0) r.x = br.x - r.width - 0.01f;
                else if (player.vel.x < 0) r.x = br.x + br.width + 0.01f;
                player.vel.x = 0;
            }
        }
        player.pos.x = r.x;
        return player.pos.x - before; // 실제 이동량(대시 거리 관리용)
    }

    private void resolveY(Rectangle r, boolean movingUp) {
        for (int i = blocks.size-1; i >= 0; i--) {
            Block b = blocks.get(i);
            Rectangle br = b.getBounds();
            if (r.overlaps(br)) {
                if (b.type == Block.Type.GOAL) continue; // GOAL은 통과
                // Y축에서는 파괴하지 않음. 헤딩일 때만 BREAKABLE 파괴.
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
                    } else if (player.vel.y > 0) {
                        r.y = br.y - r.height - 0.01f;
                        player.vel.y = 0;
                    }
                }
            }
        }
    }

    private boolean isStandingOnBlock(Rectangle r) {
        Rectangle below = new Rectangle(r.x, r.y - 2, r.width, r.height);
        for (Block b : blocks) {
            if (b.type != Block.Type.GOAL && below.overlaps(b.getBounds())) return true;
        }
        return false;
    }

    private void checkGoal(Rectangle r) {
        for (int i = 0; i < blocks.size; i++) {
            Block b = blocks.get(i);
            if (b.type == Block.Type.GOAL && r.overlaps(b.getBounds())) {
                nextLevel();
                return;
            }
        }
    }
}
