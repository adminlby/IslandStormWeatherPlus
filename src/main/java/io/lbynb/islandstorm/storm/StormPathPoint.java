package io.lbynb.islandstorm.storm;

/**
 * 风暴路径上的一个点：世界坐标 X/Z + 从路径开始算起的到达时间（毫秒）+ 该点处的强度倍率。
 *
 * <p>到达时间统一以「自路径启动后的现实毫秒」存储；指令层（P4）支持用
 * REAL_TIME / MC_TIME 输入，再经 GameTimeUtil 换算为现实毫秒后存入这里。</p>
 *
 * <p>{@code intensity} 为「该段强度倍率」（默认 1.0）：风暴中心移动到该点附近时，半径内的风力
 * 增益会乘以沿路径线性插值得到的强度，因此导演可让台风在某一段更猛、某一段减弱。</p>
 *
 * <p>{@code radius} 为「该点处影响半径」（{@code <=0} 表示沿用路径默认半径）：导演可让台风在某段
 * 扩大或收缩范围，沿路径线性插值。</p>
 */
public class StormPathPoint {

    private final double x;
    private final double z;
    private final long arriveAfterMillis;
    private final double intensity;
    /** 该点处影响半径；<=0 表示沿用路径默认半径。 */
    private final double radius;

    public StormPathPoint(double x, double z, long arriveAfterMillis) {
        this(x, z, arriveAfterMillis, 1.0, 0);
    }

    public StormPathPoint(double x, double z, long arriveAfterMillis, double intensity) {
        this(x, z, arriveAfterMillis, intensity, 0);
    }

    public StormPathPoint(double x, double z, long arriveAfterMillis, double intensity, double radius) {
        this.x = x;
        this.z = z;
        this.arriveAfterMillis = arriveAfterMillis;
        this.intensity = (intensity <= 0 || Double.isNaN(intensity)) ? 1.0 : intensity;
        this.radius = (Double.isNaN(radius) || radius < 0) ? 0 : radius;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    public long arriveAfterMillis() {
        return arriveAfterMillis;
    }

    public long arriveAfterSeconds() {
        return arriveAfterMillis / 1000L;
    }

    /** 该点处的强度倍率（默认 1.0）。 */
    public double intensity() {
        return intensity;
    }

    /** 该点处影响半径；<=0 表示沿用路径默认半径。 */
    public double radius() {
        return radius;
    }
}
