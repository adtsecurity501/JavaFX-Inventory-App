package assettracking.ui;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConfettiManager {

    private final Pane rootPane;
    private final Random random = new Random();
    private AnimationTimer timer;
    private Canvas canvas;
    private List<ConfettiParticle> particles;

    public ConfettiManager(Pane rootPane) {
        this.rootPane = rootPane;
    }

    public void start() {
        if (rootPane == null) {
            System.err.println("ConfettiManager: Root pane is null. Cannot start animation.");
            return;
        }

        canvas = new Canvas(rootPane.getWidth(), rootPane.getHeight());
        canvas.widthProperty().bind(rootPane.widthProperty());
        canvas.heightProperty().bind(rootPane.heightProperty());
        canvas.setMouseTransparent(true);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        particles = new ArrayList<>();
        int numberOfParticles = 300;
        for (int i = 0; i < numberOfParticles; i++) {
            particles.add(new ConfettiParticle());
        }

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateAndDraw(gc);
            }
        };

        rootPane.getChildren().add(canvas);
        timer.start();

        PauseTransition delay = new PauseTransition(Duration.seconds(5));
        delay.setOnFinished(event -> stop());
        delay.play();
    }

    private void stop() {
        if (timer != null) {
            timer.stop();
        }
        if (rootPane != null && canvas != null) {
            rootPane.getChildren().remove(canvas);
        }
    }

    private void updateAndDraw(GraphicsContext gc) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (ConfettiParticle p : particles) {
            p.update();
            p.draw(gc);
            if (p.y > canvas.getHeight()) {
                p.reset();
            }
        }
    }

    private class ConfettiParticle {
        private double x, y;
        private double velX, velY;
        private double size;
        private Color color;
        private double rotation;
        private double rotationSpeed;

        ConfettiParticle() {
            reset();
        }

        void reset() {
            x = random.nextDouble() * canvas.getWidth();
            y = -random.nextDouble() * canvas.getHeight();
            size = random.nextDouble() * 8 + 4;
            velX = random.nextDouble() * 4 - 2;
            velY = random.nextDouble() * 2 + 3;
            rotation = random.nextDouble() * 360;
            rotationSpeed = random.nextDouble() * 4 - 2;
            Color[] colors = {Color.YELLOW, Color.LIMEGREEN, Color.DODGERBLUE, Color.RED, Color.ORANGE, Color.MAGENTA, Color.CYAN};
            color = colors[random.nextInt(colors.length)];
        }

        void update() {
            y += velY;
            x += velX;
            rotation += rotationSpeed;
            velX += random.nextDouble() * 0.2 - 0.1;
        }

        void draw(GraphicsContext gc) {
            gc.save();
            gc.setFill(color);
            gc.translate(x, y);
            gc.rotate(rotation);
            gc.fillRect(-size / 2, -size / 2, size, size);
            gc.restore();
        }
    }
}