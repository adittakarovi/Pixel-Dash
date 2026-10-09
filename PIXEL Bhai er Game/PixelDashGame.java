import javax.swing.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

// ============================================================
//  PIXEL DASH – Main Game Class
// ============================================================
public class PixelDashGame extends JPanel implements ActionListener, KeyListener {

    // ── Window ───────────────────────────────────────────────
    private static final int WIDTH  = 1400;
    private static final int HEIGHT = 700;

    // ── Core ─────────────────────────────────────────────────
    private Timer       timer;
    private Player      player;
    private Boss        boss;
    private SoundEngine sound;
    private Random      random = new Random();

    // ── Lists ────────────────────────────────────────────────
    private ArrayList<GameObject> obstacles = new ArrayList<>();
    private ArrayList<Cloud>      clouds    = new ArrayList<>();
    private ArrayList<Particle>   particles = new ArrayList<>();
    private ArrayList<PowerUp>    powerUps  = new ArrayList<>();

    // ── Score / Speed ─────────────────────────────────────────
    private int score      = 0;
    private int highScore  = 0;
    private int gameSpeed  = 6;
    private int frameCount = 0;
    private int bgOffset   = 0;

    // ── Level ─────────────────────────────────────────────────
    private int     currentLevel     = 1;
    private boolean showLevelBanner  = false;
    private int     levelBannerTimer = 0;

    // ── State flags ───────────────────────────────────────────
    private boolean gameStarted  = false;
    private boolean gameOver     = false;
    private boolean bossMode     = false;
    private boolean bossDefeated = false;

    // ── Level data ────────────────────────────────────────────
    private static final int[] LEVEL_SCORES = { 0, 3000, 8000, 15000, 25000, 35000 };
    private static final int[] LEVEL_SPEEDS = { 6,    7,    8,     9,    10,    10  };

    private static final Color[] LEVEL_COLORS = {
        new Color( 70, 170, 255),   // 1 – Grasslands (day blue)
        new Color(255, 180,  60),   // 2 – Sunset
        new Color(140,  80, 200),   // 3 – Twilight
        new Color( 20,  20,  60),   // 4 – Midnight
        new Color(180,  30,  30),   // 5 – Danger Zone
        new Color(  5,   5,  15),   // 6 – Boss Void
    };

    private static final String[] LEVEL_NAMES = {
        "Level 1 – Grasslands",
        "Level 2 – Sunset Run",
        "Level 3 – Twilight Trail",
        "Level 4 – Midnight Rush",
        "Level 5 – Danger Zone",
        "BOSS FIGHT!",
    };

    // ============================================================
    //  Constructor
    // ============================================================
    public PixelDashGame() {
        sound  = new SoundEngine();
        player = new Player(100, 570);

        // Configure panel properties for accurate rendering & keyboard input
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);
        this.addKeyListener(this);

        for (int i = 0; i < 6; i++) {
            clouds.add(new Cloud(random.nextInt(WIDTH), random.nextInt(200)));
        }

        JFrame frame = new JFrame("PIXEL DASH");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(this);
        frame.pack(); // Automatically sizes window around panel canvas
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        this.requestFocusInWindow();

        timer = new Timer(16, this);
        timer.start();
    }

    // ============================================================
    //  Game Loop
    // ============================================================
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameStarted && !gameOver) {
            updateGame();
        }
        frameCount++;
        repaint();
    }

    // ============================================================
    //  Update
    // ============================================================
    private void updateGame() {
        bgOffset = (bgOffset + gameSpeed / 2) % WIDTH;

        checkLevelProgress();
        player.update();
        updateClouds();
        updateParticles();

        if (bossMode && boss != null) {
            updateBossMode();
            return;
        }

        updateObstacles();
        updatePowerUps();

        score += gameSpeed;
    }

    // ── Level check ───────────────────────────────────────────
    private void checkLevelProgress() {
        int newLevel = 1;

        for (int i = LEVEL_SCORES.length - 1; i >= 0; i--) {
            if (score >= LEVEL_SCORES[i]) {
                newLevel = i + 1;
                break;
            }
        }

        if (newLevel > currentLevel) {
            currentLevel = newLevel;
            gameSpeed    = LEVEL_SPEEDS[currentLevel - 1];

            showLevelBanner  = true;
            levelBannerTimer = 120;

            sound.playLevelUp();

            if (currentLevel == 6 && !bossMode) {
                bossMode = true;
                boss     = new Boss(WIDTH, 380);
                obstacles.clear();
            }
        }

        if (levelBannerTimer > 0) {
            levelBannerTimer--;
        } else {
            showLevelBanner = false;
        }
    }

    // ── Clouds ────────────────────────────────────────────────
    private void updateClouds() {
        for (Cloud c : clouds) {
            c.update(Math.max(1, gameSpeed / 2));
            if (c.getX() < -100) {
                c.setX(WIDTH + random.nextInt(300));
            }
        }
    }

    // ── Particles ─────────────────────────────────────────────
    private void updateParticles() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (p.isDead()) {
                it.remove();
            }
        }
    }

    // ── Boss mode ─────────────────────────────────────────────
    private void updateBossMode() {
        boss.update(gameSpeed, frameCount);

        // Projectile hits player
        for (BossProjectile bp : boss.getProjectiles()) {
            if (!bp.used && player.getBounds().intersects(bp.getBounds())) {
                bp.used = true;
                spawnParticles(player.getX(), player.getY(), Color.RED, 12);
                sound.playHit();
                triggerGameOver();
                return;
            }
        }

        Rectangle playerBox = player.getBounds();

        // Jump on boss head
        if (playerBox.intersects(boss.getHeadBounds()) && player.isFalling()) {
            boss.takeDamage(1);
            player.bounceOff();
            spawnParticles(boss.getX() + 40, boss.getY(), Color.ORANGE, 20);
            sound.playHit();

            if (boss.isDead()) {
                bossDefeated = true;
                bossMode     = false;
                boss         = null;
                score       += 10000;
                spawnParticles(WIDTH / 2, HEIGHT / 2, Color.YELLOW, 60);
                sound.playLevelUp();
            }
        } else if (playerBox.intersects(boss.getBodyBounds())) {
            sound.playHit();
            triggerGameOver();
            return;
        }

        score += gameSpeed;
    }

    // ── Obstacles ─────────────────────────────────────────────
    private void updateObstacles() {
        int spawnRate = Math.max(35, 90 - gameSpeed * 5);

        if (frameCount % spawnRate == 0) {
            int size = random.nextInt(25) + 20;
            int type = random.nextInt(3);
            obstacles.add(new Obstacle(WIDTH, 590, size, size, type));
        }

        // Double-wave at higher scores
        if (score > 8000 && frameCount % (spawnRate * 3) == 0) {
            obstacles.add(new Obstacle(WIDTH + 80, 590, 25, 25, 0));
        }

        Iterator<GameObject> it = obstacles.iterator();
        while (it.hasNext()) {
            GameObject o = it.next();
            o.update(gameSpeed);

            if (player.getBounds().intersects(o.getBounds())) {
                spawnParticles(player.getX(), player.getY(), Color.RED, 20);
                sound.playHit();
                triggerGameOver();
                return;
            }

            if (o.getX() < -50) {
                score += 10;
                it.remove();
            }
        }
    }

    // ── Power-ups ─────────────────────────────────────────────
    private void updatePowerUps() {
        if (frameCount % 300 == 0 && random.nextInt(2) == 0) {
            powerUps.add(new PowerUp(WIDTH, 520));
        }

        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) {
            PowerUp pu = it.next();
            pu.update(gameSpeed);

            if (player.getBounds().intersects(pu.getBounds())) {
                player.grantDoubleJump();
                spawnParticles(pu.getX(), pu.getY(), Color.CYAN, 15);
                sound.playPowerUp();
                it.remove();
                continue;
            }

            if (pu.getX() < -50) {
                it.remove();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private void triggerGameOver() {
        gameOver = true;
        if (score > highScore) {
            highScore = score;
        }
        sound.playGameOver();
    }

    private void spawnParticles(int x, int y, Color c, int count) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, c, random));
        }
    }

    private void restart() {
        obstacles.clear();
        particles.clear();
        powerUps.clear();

        score        = 0;
        gameSpeed    = 6;
        frameCount   = 0;
        bgOffset     = 0;
        currentLevel = 1;

        gameOver        = false;
        bossMode        = false;
        bossDefeated    = false;
        showLevelBanner = false;
        gameStarted     = true;
        boss            = null;

        player.reset();
    }

    // ============================================================
    //  Rendering
    // ============================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (!gameStarted) {
            drawHomeScreen(g2);
            return;
        }

        drawBackground(g2);
        drawWorld(g2);
        drawEntities(g2);
        drawHUD(g2);
        drawOverlays(g2);
    }

    // ── Background ────────────────────────────────────────────
    private void drawBackground(Graphics2D g2) {
        Color sky = LEVEL_COLORS[Math.min(currentLevel - 1, LEVEL_COLORS.length - 1)];
        g2.setColor(sky);
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        // Stars (night levels)
        if (currentLevel >= 4) {
            g2.setColor(new Color(255, 255, 255, 160));
            Random sr = new Random(42);
            for (int i = 0; i < 80; i++) {
                g2.fillOval(sr.nextInt(WIDTH), sr.nextInt(400), 2, 2);
            }
        }

        // Sun / Moon
        if (currentLevel <= 2) {
            g2.setColor(Color.YELLOW);
            g2.fillOval(1200, 50, 80, 80);
        } else if (currentLevel <= 4) {
            g2.setColor(new Color(200, 200, 220));
            g2.fillOval(1200, 50, 70, 70);
            g2.setColor(sky);
            g2.fillOval(1215, 45, 70, 70);
        } else {
            g2.setColor(new Color(200, 40, 40, 180));
            g2.fillOval(1200, 50, 70, 70);
        }

        // Clouds
        for (Cloud c : clouds) {
            c.draw(g2);
        }

        // Ground
        Color ground = currentLevel >= 5 ? new Color(60, 10, 10)
                     : currentLevel >= 4 ? new Color(20, 20, 40)
                     : Color.DARK_GRAY;
        g2.setColor(ground);
        g2.fillRect(0, 620, WIDTH, 80);

        // Ground lines
        g2.setColor(currentLevel >= 4 ? new Color(80, 0, 0) : new Color(100, 100, 100));
        g2.setStroke(new BasicStroke(2));
        for (int x = -(bgOffset % 60); x < WIDTH; x += 60) {
            g2.drawLine(x, 622, x + 30, 622);
        }
    }

    // ── World objects ─────────────────────────────────────────
    private void drawWorld(Graphics2D g2) {
        for (Particle p   : particles) p.draw(g2);
        for (PowerUp  pu  : powerUps)  pu.draw(g2);
        for (GameObject o : obstacles) o.draw(g2);
        if (bossMode && boss != null)  boss.draw(g2);
    }

    // ── Entities ──────────────────────────────────────────────
    private void drawEntities(Graphics2D g2) {
        player.draw(g2);
    }

    // ── HUD ───────────────────────────────────────────────────
    private void drawHUD(Graphics2D g2) {
        // Score panel
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(15, 15, 300, 150, 20, 20);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
        g2.drawString("SCORE : " + score,     35,  55);
        g2.drawString("BEST  : " + highScore, 35,  80);
        g2.drawString("SPEED : " + gameSpeed, 35, 105);
        g2.drawString(LEVEL_NAMES[Math.min(currentLevel - 1, LEVEL_NAMES.length - 1)], 35, 130);

        // Double-jump indicator
        if (player.hasDoubleJump()) {
            g2.setColor(new Color(0, 255, 255, 220));
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            g2.drawString("✦ DOUBLE JUMP READY", 35, 155);
        }

        // Boss HP bar
        if (bossMode && boss != null) {
            drawBossHealthBar(g2);
        }
    }

    private void drawBossHealthBar(Graphics2D g2) {
        int bw = 400, bh = 24;
        int bx = (WIDTH - bw) / 2;
        int by = 20;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(bx - 5, by - 5, bw + 10, bh + 30, 10, 10);

        g2.setColor(new Color(60, 0, 0));
        g2.fillRoundRect(bx, by, bw, bh, 8, 8);

        float ratio = (float) boss.getHp() / boss.getMaxHp();
        g2.setColor(new Color(220, 30 + (int)(80 * ratio), 30));
        g2.fillRoundRect(bx, by, (int)(bw * ratio), bh, 8, 8);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(bx, by, bw, bh, 8, 8);

        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.drawString("BOSS HP", bx + bw / 2 - 28, by + bh + 15);
    }

    // ── Overlays ──────────────────────────────────────────────
    private void drawOverlays(Graphics2D g2) {
        if (showLevelBanner)  drawLevelBanner(g2);
        if (bossDefeated)     drawBossDefeatedBanner(g2);
        if (gameOver)         drawGameOver(g2);
    }

    private void drawLevelBanner(Graphics2D g2) {
        float alpha = Math.min(1f, levelBannerTimer / 30f);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(WIDTH / 2 - 280, HEIGHT / 2 - 50, 560, 90, 20, 20);

        g2.setFont(new Font("Arial", Font.BOLD, 44));
        String txt = LEVEL_NAMES[Math.min(currentLevel - 1, LEVEL_NAMES.length - 1)];
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.YELLOW);
        g2.drawString(txt, (WIDTH - fm.stringWidth(txt)) / 2, HEIGHT / 2 + 10);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private void drawBossDefeatedBanner(Graphics2D g2) {
        if (frameCount % 60 >= 40) return;

        String txt = "BOSS DEFEATED!  +10000 pts";
        g2.setFont(new Font("Arial", Font.BOLD, 50));
        FontMetrics fm = g2.getFontMetrics();
        int x = (WIDTH - fm.stringWidth(txt)) / 2;

        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawString(txt, x + 3, HEIGHT / 2 + 3);
        g2.setColor(Color.YELLOW);
        g2.drawString(txt, x, HEIGHT / 2);
    }

    private void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        g2.setFont(new Font("Arial", Font.BOLD, 80));
        String title = "GAME OVER";
        FontMetrics fm1 = g2.getFontMetrics();
        int tx = (WIDTH - fm1.stringWidth(title)) / 2;

        g2.setColor(new Color(200, 30, 30));
        g2.drawString(title, tx + 4, 284);
        g2.setColor(Color.WHITE);
        g2.drawString(title, tx, 280);

        g2.setFont(new Font("Arial", Font.BOLD, 36));
        FontMetrics fm2 = g2.getFontMetrics();

        String finalScore   = "FINAL SCORE : " + score;
        String highScoreStr = "HIGH SCORE  : " + highScore;

        g2.setColor(Color.WHITE);
        g2.drawString(finalScore,   (WIDTH - fm2.stringWidth(finalScore))   / 2, 370);
        g2.drawString(highScoreStr, (WIDTH - fm2.stringWidth(highScoreStr)) / 2, 420);

        if ((frameCount / 25) % 2 == 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 30));
            String restart = "PRESS  R  TO RESTART";
            FontMetrics fm3 = g2.getFontMetrics();
            g2.setColor(Color.YELLOW);
            g2.drawString(restart, (WIDTH - fm3.stringWidth(restart)) / 2, 500);
        }
    }

    // ── Home screen ───────────────────────────────────────────
    private void drawHomeScreen(Graphics2D g2) {
        GradientPaint bg = new GradientPaint(0, 0, new Color(10, 10, 35),
                                             0, HEIGHT, new Color(30, 30, 70));
        g2.setPaint(bg);
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        g2.setColor(new Color(255, 255, 255, 130));
        Random sr = new Random(7);
        for (int i = 0; i < 140; i++) {
            g2.fillOval(sr.nextInt(WIDTH), sr.nextInt(HEIGHT), 2, 2);
        }

        g2.setFont(new Font("Arial", Font.BOLD, 90));
        String title = "PIXEL DASH";
        FontMetrics fm = g2.getFontMetrics();
        int tx = (WIDTH - fm.stringWidth(title)) / 2;

        for (int r = 18; r > 0; r -= 3) {
            g2.setColor(new Color(0, 200, 255, 12));
            g2.drawString(title, tx - r / 2, 190 - r / 2);
        }

        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(title, tx + 5, 190);
        g2.setColor(Color.CYAN);
        g2.drawString(title, tx, 185);

        g2.setFont(new Font("Arial", Font.PLAIN, 30));
        String sub = "ENDLESS RUNNER  •  BOSS FIGHT  •  LEVEL SYSTEM";
        FontMetrics fm2 = g2.getFontMetrics();
        g2.setColor(Color.WHITE);
        g2.drawString(sub, (WIDTH - fm2.stringWidth(sub)) / 2, 245);

        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillRoundRect(380, 290, 640, 280, 30, 30);
        g2.setColor(new Color(0, 255, 255, 100));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(380, 290, 640, 280, 30, 30);

        g2.setFont(new Font("Arial", Font.BOLD, 30));
        String ctrl = "CONTROLS";
        FontMetrics fm3 = g2.getFontMetrics();
        g2.setColor(Color.WHITE);
        g2.drawString(ctrl, (WIDTH - fm3.stringWidth(ctrl)) / 2, 345);

        g2.setFont(new Font("Arial", Font.PLAIN, 24));
        FontMetrics fm4 = g2.getFontMetrics();
        String[] controls = {
            "ENTER   →   Start Game",
            "SPACE   →   Jump  /  Double-jump (with power-up)",
            "R       →   Restart after Game Over",
            "BOSS at 35,000 pts  –  jump on its head!",
        };

        int ly = 395;
        for (String line : controls) {
            g2.drawString(line, (WIDTH - fm4.stringWidth(line)) / 2, ly);
            ly += 42;
        }

        if ((frameCount / 30) % 2 == 0) {
            g2.setFont(new Font("Arial", Font.BOLD, 32));
            String start = "PRESS  ENTER  TO  PLAY";
            FontMetrics fm5 = g2.getFontMetrics();
            g2.setColor(Color.YELLOW);
            g2.drawString(start, (WIDTH - fm5.stringWidth(start)) / 2, 625);
        }

        g2.setFont(new Font("Arial", Font.BOLD, 18));
        String footer = "Enhanced Edition";
        FontMetrics fm6 = g2.getFontMetrics();
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawString(footer, (WIDTH - fm6.stringWidth(footer)) / 2, 655);
    }

    // ============================================================
    //  Input
    // ============================================================
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (k == KeyEvent.VK_ENTER && !gameStarted) {
            gameStarted = true;
        }

        if (k == KeyEvent.VK_SPACE && gameStarted && !gameOver) {
            player.jump();
            sound.playJump();
        }

        if (k == KeyEvent.VK_R && gameOver) {
            restart();
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e)    {}

    // ============================================================
    //  Entry Point
    // ============================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(PixelDashGame::new);
    }
}

// ============================================================
//  SOUND ENGINE
// ============================================================
class SoundEngine {

    private Synthesizer   synth;
    private MidiChannel[] channels;
    private boolean       ready = false;

    public SoundEngine() {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channels = synth.getChannels();
            ready = true;
        } catch (Exception ignored) {}
    }

    public void playJump() {
        new Thread(() -> {
            if (!ready) return;
            try {
                channels[0].programChange(80);
                for (int n = 60; n <= 72; n += 4) {
                    channels[0].noteOn(n, 90);
                    Thread.sleep(25);
                    channels[0].noteOff(n);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public void playHit() {
        new Thread(() -> {
            if (!ready) return;
            try {
                channels[9].noteOn(38, 120);
                Thread.sleep(80);
                channels[9].noteOff(38);
                channels[9].noteOn(36, 100);
                Thread.sleep(60);
                channels[9].noteOff(36);
            } catch (Exception ignored) {}
        }).start();
    }

    public void playGameOver() {
        new Thread(() -> {
            if (!ready) return;
            try {
                channels[1].programChange(19);
                for (int n : new int[]{60, 58, 56, 53}) {
                    channels[1].noteOn(n, 100);
                    Thread.sleep(200);
                    channels[1].noteOff(n);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public void playLevelUp() {
        new Thread(() -> {
            if (!ready) return;
            try {
                channels[2].programChange(9);
                for (int n : new int[]{60, 64, 67, 72}) {
                    channels[2].noteOn(n, 110);
                    Thread.sleep(100);
                    channels[2].noteOff(n);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public void playPowerUp() {
        new Thread(() -> {
            if (!ready) return;
            try {
                channels[3].programChange(98);
                for (int n : new int[]{72, 76, 79}) {
                    channels[3].noteOn(n, 100);
                    Thread.sleep(70);
                    channels[3].noteOff(n);
                }
            } catch (Exception ignored) {}
        }).start();
    }
}

// ============================================================
//  PLAYER
// ============================================================
class Player {

    private int     x, y;
    private double  vy           = 0;
    private boolean ground       = true;
    private boolean djAvailable  = false;
    private boolean djUsed       = false;

    private static final int    SIZE       = 45;
    private static final double GRAVITY    = 0.65;
    private static final double JUMP_FORCE = -13.0;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update() {
        vy += GRAVITY;
        y  += (int) vy;

        if (y >= 570) {
            y      = 570;
            vy     = 0;
            ground = true;
            djUsed = false;
        }
    }

    public void draw(Graphics g) {
        Graphics2D g2      = (Graphics2D) g;
        int        legMove = (int)(Math.sin(System.currentTimeMillis() * 0.012) * 5);

        // Double-jump aura
        if (djAvailable && !djUsed) {
            g2.setColor(new Color(0, 255, 255, 50));
            g2.fillOval(x - 10, y - 10, 70, 80);
        }

        // Shadow
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillOval(x + 5, y + 45, 38, 10);

        // Body
        g2.setColor(new Color(30, 220, 120));
        g2.fillRoundRect(x + 5, y + 18, 35, 30, 15, 15);

        g2.setColor(new Color(20, 180, 100));
        g2.fillRoundRect(x + 8, y + 20, 29, 20, 10, 10);

        // Head
        g2.setColor(new Color(255, 220, 180));
        g2.fillOval(x + 8, y, 30, 30);

        // Eyes
        g2.setColor(Color.BLACK);
        g2.fillOval(x + 16, y + 10, 4, 4);
        g2.fillOval(x + 28, y + 10, 4, 4);

        // Smile
        g2.setStroke(new BasicStroke(2));
        g2.drawArc(x + 16, y + 14, 14, 8, 180, 180);

        // Arms
        g2.setStroke(new BasicStroke(3));
        g2.drawLine(x +  5, y + 28, x -  2, y + 36);
        g2.drawLine(x + 40, y + 28, x + 47, y + 36);

        // Legs
        g2.drawLine(x + 15, y + 48, x + 10, y + 58 + legMove);
        g2.drawLine(x + 30, y + 48, x + 35, y + 58 - legMove);

        // Shoes
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(x +  6, y + 56 + legMove, 10, 4, 4, 4);
        g2.fillRoundRect(x + 30, y + 56 - legMove, 10, 4, 4, 4);
    }

    public void jump() {
        if (ground) {
            vy     = JUMP_FORCE;
            ground = false;
        } else if (djAvailable && !djUsed) {
            vy     = JUMP_FORCE * 0.85;
            djUsed = true;
        }
    }

    public void bounceOff()        { vy = -9; ground = false; }
    public boolean isFalling()     { return vy > 0; }
    public void grantDoubleJump()  { djAvailable = true;  djUsed = false; }
    public boolean hasDoubleJump() { return djAvailable && !djUsed; }
    public int getX()              { return x; }
    public int getY()              { return y; }

    public Rectangle getBounds() {
        return new Rectangle(x + 5, y + 5, SIZE - 10, SIZE - 5);
    }

    public void reset() {
        y           = 570;
        vy          = 0;
        ground      = true;
        djAvailable = false;
        djUsed      = false;
    }
}

// ============================================================
//  GAME OBJECT (abstract base)
// ============================================================
abstract class GameObject {

    protected int x, y, w, h;

    public GameObject(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public abstract void draw(Graphics g);
    public abstract void update(int speed);

    public Rectangle getBounds() { return new Rectangle(x, y, w, h); }
    public int       getX()      { return x; }
}

// ============================================================
//  OBSTACLE (3 types: spike | crate | tall spike)
// ============================================================
class Obstacle extends GameObject {

    private final int type;

    public Obstacle(int x, int y, int w, int h, int type) {
        super(x, y, w, h);
        this.type = type;
    }

    @Override
    public void update(int speed) {
        x -= speed;
    }

    @Override
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        switch (type) {
            case 0: // Red spike
                g2.setColor(new Color(220, 30, 30));
                g2.fillPolygon(new int[]{x, x + w/2, x + w},
                               new int[]{y + h, y, y + h}, 3);
                g2.setColor(new Color(255, 100, 100));
                g2.drawPolygon(new int[]{x, x + w/2, x + w},
                               new int[]{y + h, y, y + h}, 3);
                break;

            case 1: // Wooden crate
                g2.setColor(new Color(160, 90, 30));
                g2.fillRect(x, y, w, h);
                g2.setColor(new Color(200, 130, 60));
                g2.drawRect(x, y, w, h);
                g2.drawLine(x, y, x + w, y + h);
                g2.drawLine(x + w, y, x, y + h);
                break;

            case 2: // Tall purple spike
                g2.setColor(new Color(180, 0, 180));
                g2.fillPolygon(new int[]{x, x + w/2, x + w},
                               new int[]{y + h, y - 10, y + h}, 3);
                g2.setColor(new Color(220, 100, 220));
                g2.drawPolygon(new int[]{x, x + w/2, x + w},
                               new int[]{y + h, y - 10, y + h}, 3);
                break;
        }
    }
}

// ============================================================
//  CLOUD
// ============================================================
class Cloud {

    private int x, y;

    public Cloud(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update(int speed) {
        x -= speed;
    }

    public void draw(Graphics g) {
        g.setColor(new Color(255, 255, 255, 180));
        g.fillOval(x,      y,      70, 35);
        g.fillOval(x + 20, y - 12, 55, 42);
        g.fillOval(x + 45, y +  5, 50, 28);
    }

    public int  getX()        { return x; }
    public void setX(int val) { x = val;  }
}

// ============================================================
//  PARTICLE
// ============================================================
class Particle {

    private float x, y, vx, vy;
    private final Color color;
    private int life, maxLife;

    public Particle(int x, int y, Color c, Random rnd) {
        this.x   = x;
        this.y   = y;
        this.color = c;
        vx      = (rnd.nextFloat() - 0.5f) * 6;
        vy      = (rnd.nextFloat() - 0.5f) * 6 - 2;
        maxLife = life = 30 + rnd.nextInt(20);
    }

    public void update() {
        x   += vx;
        y   += vy;
        vy  += 0.2f;
        life--;
    }

    public boolean isDead() {
        return life <= 0;
    }

    public void draw(Graphics g) {
        float alpha = (float) life / maxLife;
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                            (int)(alpha * 220));
        g.setColor(c);
        g.fillOval((int) x, (int) y, 6, 6);
    }
}

// ============================================================
//  POWER-UP (double-jump star)
// ============================================================
class PowerUp {

    private int x, y;
    private int frame = 0;

    public PowerUp(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update(int speed) {
        x -= speed;
        frame++;
    }

    public void draw(Graphics g) {
        Graphics2D g2  = (Graphics2D) g;
        int        bob = (int)(Math.sin(frame * 0.1) * 5);

        g2.setColor(new Color(0, 255, 255, 200));
        drawStar(g2, x + 14, y + 14 + bob, 14, 7, 5);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 9));
        g2.drawString("2x", x + 8, y + 18 + bob);
    }

    private void drawStar(Graphics2D g2, int cx, int cy,
                          int outer, int inner, int points) {
        double angle = -Math.PI / 2;
        double step  =  Math.PI / points;
        int[]  xs    = new int[points * 2];
        int[]  ys    = new int[points * 2];

        for (int i = 0; i < points * 2; i++) {
            int r = (i % 2 == 0) ? outer : inner;
            xs[i] = (int)(cx + r * Math.cos(angle));
            ys[i] = (int)(cy + r * Math.sin(angle));
            angle += step;
        }
        g2.fillPolygon(xs, ys, points * 2);
    }

    public int       getX()      { return x; }
    public int       getY()      { return y; }
    public Rectangle getBounds() { return new Rectangle(x, y, 28, 28); }
}

// ============================================================
//  BOSS PROJECTILE
// ============================================================
class BossProjectile {

    float   x, y, vx, vy;
    boolean used = false;

    public BossProjectile(float x, float y, float vx, float vy) {
        this.x  = x;
        this.y  = y;
        this.vx = vx;
        this.vy = vy;
    }

    public void update() {
        x  += vx;
        y  += vy;
        vy += 0.15f;
    }

    public void draw(Graphics g) {
        g.setColor(new Color(255, 80, 0, 220));
        g.fillOval((int) x - 8, (int) y - 8, 16, 16);
        g.setColor(Color.YELLOW);
        g.fillOval((int) x - 4, (int) y - 4,  8,  8);
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x - 8, (int) y - 8, 16, 16);
    }
}

// ============================================================
//  BOSS
// ============================================================
class Boss {

    private float x, y;
    private float targetX;
    private int   hp, maxHp;
    private int   phase         = 1;   // 1=normal, 2=enraged
    private int   shootCooldown = 0;
    private int   bobFrame      = 0;

    private ArrayList<BossProjectile> projectiles = new ArrayList<>();

    private static final int ARENA_WIDTH = 1400;

    public Boss(int startX, int startY) {
        x       = startX;
        y       = startY;
        hp      = maxHp = 20;
        targetX = 900;
    }

    // ── Update ────────────────────────────────────────────────
    public void update(int gameSpeed, int frame) {
        bobFrame++;

        // Drift toward target
        float dx = targetX - x;
        if (Math.abs(dx) > 3) {
            x += dx * 0.02f;
        }

        // Reposition periodically
        if (frame % 180 == 0) {
            targetX = 700 + (float)(Math.random() * 300);
        }

        // Shoot projectiles
        shootCooldown--;
        int rate = (phase == 2) ? 55 : 90;

        if (shootCooldown <= 0) {
            shootCooldown = rate;
            for (int i = -1; i <= 1; i++) {
                float angle = (float)(Math.PI + i * 0.35);
                projectiles.add(new BossProjectile(
                    x + 50, y + 80,
                    (float)(Math.cos(angle) * 5),
                    (float)(Math.sin(angle) * 5)
                ));
            }
        }

        // Update & cull projectiles
        Iterator<BossProjectile> it = projectiles.iterator();
        while (it.hasNext()) {
            BossProjectile bp = it.next();
            bp.update();
            if (bp.x < -30 || bp.x > ARENA_WIDTH + 30 || bp.y > 700 || bp.used) {
                it.remove();
            }
        }

        if (hp <= maxHp / 2) {
            phase = 2;
        }
    }

    // ── Draw ──────────────────────────────────────────────────
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        int     bx      = (int) x;
        int     by      = (int)(y + Math.sin(bobFrame * 0.04) * 6);
        boolean enraged = (phase == 2);

        Color bodyColor  = enraged ? new Color(180, 20, 20) : new Color(120,  0, 160);
        Color bellyColor = enraged ? new Color(220, 80, 80) : new Color(180, 100, 220);
        Color hornColor  = enraged ? new Color(255, 100, 0) : new Color( 80,   0, 120);
        Color eyeColor   = enraged ? Color.ORANGE           : new Color(255,  60,  60);

        // Shadow
        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillOval(bx + 10, by + 135, 90, 20);

        // Body
        g2.setColor(bodyColor);
        g2.fillRoundRect(bx + 10, by + 60, 90, 80, 25, 25);
        g2.setColor(bellyColor);
        g2.fillRoundRect(bx + 25, by + 75, 60, 55, 15, 15);

        // Head
        g2.setColor(bodyColor);
        g2.fillOval(bx + 5, by, 100, 80);

        // Horns
        g2.setColor(hornColor);
        g2.fillPolygon(new int[]{bx + 20, bx + 10, bx + 30}, new int[]{by + 10, by - 25, by - 20}, 3);
        g2.fillPolygon(new int[]{bx + 90, bx + 100, bx + 80}, new int[]{by + 10, by - 25, by - 20}, 3);

        // Eyes
        g2.setColor(eyeColor);
        g2.fillOval(bx + 22, by + 22, 22, 22);
        g2.fillOval(bx + 66, by + 22, 22, 22);
        g2.setColor(Color.BLACK);
        g2.fillOval(bx + 29, by + 28, 10, 10);
        g2.fillOval(bx + 73, by + 28, 10, 10);
        g2.setColor(Color.WHITE);
        g2.fillOval(bx + 31, by + 29,  4,  4);
        g2.fillOval(bx + 75, by + 29,  4,  4);

        // Mouth & teeth
        g2.setColor(Color.BLACK);
        g2.fillArc(bx + 25, by + 45, 60, 25, 180, 180);
        g2.setColor(Color.WHITE);
        for (int t = 0; t < 5; t++) {
            g2.fillRect(bx + 30 + t * 11, by + 57, 8, 10);
        }

        // Arms
        g2.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(bodyColor);
        g2.drawLine(bx +  10, by + 80, bx -  20, by + 110);
        g2.drawLine(bx + 100, by + 80, bx + 130, by + 110);

        // Claws
        g2.setStroke(new BasicStroke(4));
        g2.setColor(new Color(255, 220, 180));
        for (int i = -1; i <= 1; i++) {
            g2.drawLine(bx -  20 + i * 6, by + 110, bx -  20 + i * 6, by + 128);
            g2.drawLine(bx + 130 + i * 6, by + 110, bx + 130 + i * 6, by + 128);
        }

        // Legs
        g2.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(bodyColor);
        int legSwing = (int)(Math.sin(bobFrame * 0.08) * 8);
        g2.drawLine(bx + 30, by + 140, bx + 20, by + 165 + legSwing);
        g2.drawLine(bx + 80, by + 140, bx + 90, by + 165 - legSwing);

        // Enraged aura
        if (enraged) {
            int auraAlpha = (int)(40 + 30 * Math.sin(bobFrame * 0.1));
            g2.setColor(new Color(255, 50, 0, auraAlpha));
            g2.setStroke(new BasicStroke(6));
            g2.drawOval(bx - 10, by - 10, 130, 165);

            g2.setFont(new Font("Arial", Font.BOLD, 16));
            g2.setColor(new Color(255, 80, 0));
            g2.drawString("⚡ ENRAGED", bx + 15, by - 30);
        }

        // Projectiles
        for (BossProjectile bp : projectiles) {
            bp.draw(g2);
        }
    }

    // ── Accessors ─────────────────────────────────────────────
    public void    takeDamage(int d) { hp = Math.max(0, hp - d); }
    public boolean isDead()          { return hp <= 0; }
    public int     getHp()           { return hp; }
    public int     getMaxHp()        { return maxHp; }
    public int     getX()            { return (int) x; }
    public int     getY()            { return (int) y; }

    public ArrayList<BossProjectile> getProjectiles() { return projectiles; }

    public Rectangle getHeadBounds() {
        return new Rectangle((int) x +  5, (int) y,      100,  50);
    }

    public Rectangle getBodyBounds() {
        return new Rectangle((int) x + 10, (int) y + 50,  90, 110);
    }
}