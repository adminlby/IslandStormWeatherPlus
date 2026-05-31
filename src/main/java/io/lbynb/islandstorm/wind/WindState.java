package io.lbynb.islandstorm.wind;

/** 风状态：风速（km/h）+ 风向，外加风速等级标签。 */
public class WindState {

    private double speed;
    private WindDirection direction;

    public WindState(double speed, WindDirection direction) {
        this.speed = speed;
        this.direction = direction;
    }

    public double speed() {
        return speed;
    }

    public WindDirection direction() {
        return direction;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setDirection(WindDirection direction) {
        this.direction = direction;
    }

    public WindState copy() {
        return new WindState(speed, direction);
    }

    /** 风速等级标签（对应需求的 6 档）。 */
    public String levelLabel() {
        if (speed <= 10) return "无影响";
        if (speed <= 30) return "轻微影响";
        if (speed <= 60) return "明显影响";
        if (speed <= 90) return "强风";
        if (speed <= 120) return "危险强风";
        return "极端危险";
    }
}
