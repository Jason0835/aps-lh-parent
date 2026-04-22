-- ============================================
-- ENABLE_PRIORITY_TRACE_LOG 迁移脚本
-- 目标：为现有工厂补齐“优先级跟踪日志开关”参数，默认值为 0
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
       'ENABLE_PRIORITY_TRACE_LOG',
       '0',
       '优先级跟踪日志开关',
       '0-关闭，1-开启',
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
    AND p.PARAM_CODE = 'ENABLE_PRIORITY_TRACE_LOG'
    AND p.IS_DELETE = 0
WHERE p.ID IS NULL;

UPDATE T_LH_PARAMS
SET PARAM_VALUE = '0',
    PARAM_NAME = '优先级跟踪日志开关',
    REMARK = '0-关闭，1-开启',
    UPDATE_BY = 'system',
    UPDATE_TIME = NOW()
WHERE PARAM_CODE = 'ENABLE_PRIORITY_TRACE_LOG'
  AND IS_DELETE = 0
  AND (PARAM_VALUE IS NULL OR PARAM_VALUE = '');
