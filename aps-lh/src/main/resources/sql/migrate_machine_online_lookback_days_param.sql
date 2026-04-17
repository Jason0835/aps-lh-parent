-- ============================================
-- MACHINE_ONLINE_LOOKBACK_DAYS 迁移脚本
-- 目标：为现有工厂补齐“MES在机信息往前追溯天数”参数，默认值为 1
-- ============================================

INSERT INTO T_LH_PARAMS (
    FACTORY_CODE,
    PARAM_CODE,
    PARAM_VALUE,
    PARAM_NAME,
    REMARK,
    CREATE_BY,
    CREATE_TIME,
    UPDATE_BY,
    UPDATE_TIME,
    IS_DELETE
)
SELECT t.FACTORY_CODE,
       'MACHINE_ONLINE_LOOKBACK_DAYS',
       '1',
       '往前追溯天数',
       'MES在机信息从T-1开始向前回溯的最大天数',
       'system',
       NOW(),
       'system',
       NOW(),
       0
FROM (
    SELECT DISTINCT FACTORY_CODE
    FROM T_LH_PARAMS
    WHERE IS_DELETE = 0
      AND FACTORY_CODE IS NOT NULL
      AND FACTORY_CODE <> ''
) t
LEFT JOIN T_LH_PARAMS p
    ON p.FACTORY_CODE = t.FACTORY_CODE
    AND p.PARAM_CODE = 'MACHINE_ONLINE_LOOKBACK_DAYS'
    AND p.IS_DELETE = 0
WHERE p.ID IS NULL;

-- 如需仅迁移指定工厂，可在子查询中追加条件：
-- AND FACTORY_CODE IN ('116');
