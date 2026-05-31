package io.lbynb.islandstorm.web.api;

import io.lbynb.islandstorm.IslandStormPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * API 处理通用支撑：在主线程同步执行 Bukkit 相关逻辑。
 *
 * <p>HttpServer 的请求运行在独立线程，而 Bukkit API 非线程安全，
 * 因此所有触及世界/玩家/天气的逻辑都必须经 {@link #runSync(IslandStormPlugin, Callable)} 切回主线程。</p>
 */
public final class ApiSupport {

    private ApiSupport() {
    }

    public static <T> T runSync(IslandStormPlugin plugin, Callable<T> task) {
        if (plugin.getServer().isPrimaryThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw new ApiException(500, "内部错误：" + e.getMessage());
            }
        }
        try {
            return plugin.getServer().getScheduler().callSyncMethod(plugin, task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "请求被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException) throw (ApiException) cause;
            throw new ApiException(500, "内部错误：" + (cause == null ? e.getMessage() : cause.getMessage()));
        }
    }
}
