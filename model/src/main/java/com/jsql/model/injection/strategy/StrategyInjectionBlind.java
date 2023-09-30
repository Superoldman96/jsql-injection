/*******************************************************************************
 * Copyhacked (H) 2012-2020.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss about it
 * every time possible with every body.
 *
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 *******************************************************************************/
package com.jsql.model.injection.strategy;

import com.jsql.model.InjectionModel;
import com.jsql.model.bean.util.Interaction;
import com.jsql.model.bean.util.Request;
import com.jsql.model.exception.StoppedByUserSlidingException;
import com.jsql.model.injection.strategy.blind.AbstractInjectionBoolean.BooleanMode;
import com.jsql.model.injection.strategy.blind.InjectionBlind;
import com.jsql.model.injection.vendor.model.VendorYaml;
import com.jsql.model.suspendable.AbstractSuspendable;
import com.jsql.util.I18nUtil;
import com.jsql.util.LogLevelUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StrategyInjectionBlind extends AbstractStrategy {
    
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = LogManager.getRootLogger();

    private InjectionBlind injectionBlind;
    
    public StrategyInjectionBlind(InjectionModel injectionModel) {
        
        super(injectionModel);
    }

    @Override
    public void checkApplicability() throws StoppedByUserSlidingException {

        if (this.injectionModel.getMediatorUtils().getPreferencesUtil().isStrategyBlindDisabled()) {

            LOGGER.log(LogLevelUtil.CONSOLE_INFORM, "Skipping strategy Blind disabled");
            return;

        } else if (StringUtils.isEmpty(this.injectionModel.getMediatorVendor().getVendor().instance().sqlBooleanBlind())) {

            LOGGER.log(LogLevelUtil.CONSOLE_INFORM, "No Blind strategy known for {}", this.injectionModel.getMediatorVendor().getVendor());
            return;
        }

        LOGGER.log(LogLevelUtil.CONSOLE_DEFAULT, "{} Blind with STACKED...", () -> I18nUtil.valueByKey(KEY_LOG_CHECKING_STRATEGY));

        this.injectionBlind = new InjectionBlind(this.injectionModel, BooleanMode.STACKED);
        this.isApplicable = this.injectionBlind.isInjectable();

        if (!this.isApplicable) {

            LOGGER.log(LogLevelUtil.CONSOLE_DEFAULT, "{} Blind with no mode...", () -> I18nUtil.valueByKey(KEY_LOG_CHECKING_STRATEGY));

            this.injectionBlind = new InjectionBlind(this.injectionModel, BooleanMode.NO_MODE);
            this.isApplicable = this.injectionBlind.isInjectable();

            if (!this.isApplicable) {

                LOGGER.log(LogLevelUtil.CONSOLE_DEFAULT, "{} Blind with OR...", () -> I18nUtil.valueByKey(KEY_LOG_CHECKING_STRATEGY));

                this.injectionBlind = new InjectionBlind(this.injectionModel, BooleanMode.OR);
                this.isApplicable = this.injectionBlind.isInjectable();

                if (!this.isApplicable) {

                    LOGGER.log(LogLevelUtil.CONSOLE_DEFAULT, "{} Blind with AND...", () -> I18nUtil.valueByKey(KEY_LOG_CHECKING_STRATEGY));

                    this.injectionBlind = new InjectionBlind(this.injectionModel, BooleanMode.AND);
                    this.isApplicable = this.injectionBlind.isInjectable();

                    if (this.isApplicable) {

                        LOGGER.log(LogLevelUtil.CONSOLE_SUCCESS, "{} Blind injection with AND", () -> I18nUtil.valueByKey(KEY_LOG_VULNERABLE));
                    }
                } else {

                    LOGGER.log(LogLevelUtil.CONSOLE_SUCCESS, "{} Blind injection with OR", () -> I18nUtil.valueByKey(KEY_LOG_VULNERABLE));
                }
            } else {

                LOGGER.log(LogLevelUtil.CONSOLE_SUCCESS, "{} Blind injection with no mode", () -> I18nUtil.valueByKey(KEY_LOG_VULNERABLE));
            }
        } else {

            LOGGER.log(LogLevelUtil.CONSOLE_SUCCESS, "{} Blind injection with STACKED", () -> I18nUtil.valueByKey(KEY_LOG_VULNERABLE));
        }

        if (this.isApplicable) {

            this.allow();

            var requestMessageBinary = new Request();
            requestMessageBinary.setMessage(Interaction.MESSAGE_BINARY);
            requestMessageBinary.setParameters(this.injectionBlind.getInfoMessage());
            this.injectionModel.sendToViews(requestMessageBinary);

        } else {

            this.unallow();
        }
    }

    @Override
    public void allow(int... i) {
        
        this.markVulnerability(Interaction.MARK_BLIND_VULNERABLE);
    }

    @Override
    public void unallow(int... i) {
        
        this.markVulnerability(Interaction.MARK_BLIND_INVULNERABLE);
    }

    @Override
    public String inject(String sqlQuery, String startPosition, AbstractSuspendable stoppable, String metadataInjectionProcess) throws StoppedByUserSlidingException {
        
        return this.injectionBlind.inject(
            this.injectionModel.getMediatorVendor().getVendor().instance().sqlBlind(sqlQuery, startPosition),
            stoppable
        );
    }

    @Override
    public void activateStrategy() {
        
        LOGGER.log(LogLevelUtil.CONSOLE_INFORM, "Using {} statement", this.injectionBlind.getBooleanMode().name());

        LOGGER.log(
            LogLevelUtil.CONSOLE_INFORM,
            "{} [{}]",
            () -> I18nUtil.valueByKey("LOG_USING_STRATEGY"),
            this::getName
        );
        this.injectionModel.getMediatorStrategy().setStrategy(this.injectionModel.getMediatorStrategy().getBlind());
        
        var requestMarkBlindStrategy = new Request();
        requestMarkBlindStrategy.setMessage(Interaction.MARK_BLIND_STRATEGY);
        this.injectionModel.sendToViews(requestMarkBlindStrategy);
    }
    
    @Override
    public String getPerformanceLength() {
        return VendorYaml.DEFAULT_CAPACITY;
    }
    
    @Override
    public String getName() {
        return "Blind";
    }
}
