package com.zlt.aps.lh.engine.template;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.handle.ContinuousProductionHandler;
import com.zlt.aps.lh.handle.DataInitHandler;
import com.zlt.aps.lh.handle.NewProductionHandler;
import com.zlt.aps.lh.handle.PreValidationHandler;
import com.zlt.aps.lh.handle.ResultValidationHandler;
import com.zlt.aps.lh.handle.ScheduleAdjustHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 硫化排程模板方法具体实现
 * <p>将各步骤委托给对应的Handler处理</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class LhScheduleTemplateImpl extends AbsLhScheduleTemplate {

    @Resource
    private PreValidationHandler preValidationHandler;

    @Resource
    private DataInitHandler dataInitHandler;

    @Resource
    private ScheduleAdjustHandler scheduleAdjustHandler;

    @Resource
    private ContinuousProductionHandler continuousProductionHandler;

    @Resource
    private NewProductionHandler newProductionHandler;

    @Resource
    private ResultValidationHandler resultValidationHandler;

    @Override
    protected void doPreValidation(LhScheduleContext context) {
        preValidationHandler.handle(context);
    }

    @Override
    protected void doDataInitialization(LhScheduleContext context) {
        dataInitHandler.handle(context);
    }

    @Override
    protected void doAdjustAndGather(LhScheduleContext context) {
        scheduleAdjustHandler.handle(context);
    }

    @Override
    protected void doContinuousProduction(LhScheduleContext context) {
        continuousProductionHandler.handle(context);
    }

    @Override
    protected void doNewSpecProduction(LhScheduleContext context) {
        newProductionHandler.handle(context);
    }

    @Override
    protected void doResultValidationAndSave(LhScheduleContext context) {
        resultValidationHandler.handle(context);
    }
}
