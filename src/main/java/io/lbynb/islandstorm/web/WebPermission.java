package io.lbynb.islandstorm.web;

import java.util.List;

/**
 * 网页控制台权限节点常量。用户权限以字符串集合存储；持有 {@code "*"} 表示拥有全部权限。
 */
public final class WebPermission {

    public static final String WEATHER_VIEW = "weather.view";
    public static final String WEATHER_SET = "weather.set";
    public static final String WIND_VIEW = "wind.view";
    public static final String WIND_SET = "wind.set";
    public static final String FORECAST_VIEW = "forecast.view";
    public static final String REGION_VIEW = "region.view";
    public static final String REGION_CREATE = "region.create";
    public static final String REGION_EDIT = "region.edit";
    public static final String REGION_DELETE = "region.delete";
    public static final String STORM_PATH_VIEW = "storm.path.view";
    public static final String STORM_PATH_EDIT = "storm.path.edit";
    public static final String BLOCKDAMAGE_VIEW = "blockdamage.view";
    public static final String BLOCKDAMAGE_EDIT = "blockdamage.edit";
    public static final String HTML_GENERATE = "html.generate";
    public static final String SERVER_STATUS = "server.status";
    public static final String USER_MANAGE = "user.manage";
    public static final String MAP_VIEW = "map.view";

    /** 所有权限节点（用于 TabComplete 与校验）。 */
    public static final List<String> ALL = List.of(
            WEATHER_VIEW, WEATHER_SET, WIND_VIEW, WIND_SET, FORECAST_VIEW,
            REGION_VIEW, REGION_CREATE, REGION_EDIT, REGION_DELETE,
            STORM_PATH_VIEW, STORM_PATH_EDIT, BLOCKDAMAGE_VIEW, BLOCKDAMAGE_EDIT,
            HTML_GENERATE, SERVER_STATUS, USER_MANAGE, MAP_VIEW);

    private WebPermission() {
    }

    public static boolean isValid(String node) {
        return "*".equals(node) || ALL.contains(node);
    }
}
