package io.lbynb.islandstorm.storm;

/**
 * 风暴路径上的一个点：世界坐标 X/Z + 从路径开始算起的到达时间（毫秒）。
 *
 * <p>到达时间统一以「自路径启动后的现实毫秒」存储；指令层（P4）支持用
 * REAL_TIME / MC_TIME 输入，再经 GameTimeUtil 换算为现实毫秒后存入这里。</p>
 */
public class StormPathPoint {

    private final double x;
    private final double z;
    private final long arriveAfterMillis;

    public StormPathPoint(double x, double z, long arriveAfterMillis) {
        this.x = x;
        this.z = z;
        this.arriveAfterMillis = arriveAfterMillis;
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
}
