-- ============================================
-- MAX_FIRST_INSPECTION_PER_SHIFT 迁移脚本
-- 目标：将历史默认值 5 迁移为 -1（不限制）
-- ============================================

-- 将当前生效参数中仍为 5 的配置改为 -1
UPDATE T_LH_PARAMS
SET PARAM_VALUE = '-1',
    UPDATE_TIME = NOW(),
    UPDATE_BY = 'system'
WHERE PARAM_CODE = 'MAX_FIRST_INSPECTION_PER_SHIFT'
  AND PARAM_VALUE = '5'
  AND IS_DELETE = '0';

-- 如需仅迁移指定工厂，可追加工厂条件后执行：
-- AND FACTORY_CODE IN ('F001');
