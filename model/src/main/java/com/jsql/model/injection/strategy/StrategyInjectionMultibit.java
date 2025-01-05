/*******************************************************************************
 * Copyhacked (H) 2012-2025.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss it
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
import com.jsql.model.injection.strategy.blind.AbstractInjectionBinary.BinaryMode;
import com.jsql.model.injection.strategy.blind.InjectionMultibit;
import com.jsql.model.injection.vendor.model.VendorYaml;
import com.jsql.model.suspendable.AbstractSuspendable;
import com.jsql.util.I18nUtil;
import com.jsql.util.LogLevelUtil;
import com.jsql.util.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StrategyInjectionMultibit extends AbstractStrategy {

    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = LogManager.getRootLogger();

    private InjectionMultibit injectionMultibit;

    public StrategyInjectionMultibit(InjectionModel injectionModel) {
        super(injectionModel);
    }

    @Override
    public void checkApplicability() throws StoppedByUserSlidingException {
        if (this.injectionModel.getMediatorUtils().getPreferencesUtil().isStrategyMultibitDisabled()) {
            LOGGER.log(LogLevelUtil.CONSOLE_INFORM, AbstractStrategy.FORMAT_SKIP_STRATEGY_DISABLED, getName());
            return;
        }
        LOGGER.log(LogLevelUtil.CONSOLE_DEFAULT, "{} Multibit...", () -> I18nUtil.valueByKey(KEY_LOG_CHECKING_STRATEGY));

        this.injectionMultibit = new InjectionMultibit(this.injectionModel, BinaryMode.STACKED);
        this.isApplicable = this.injectionMultibit.isInjectable();

        if (this.isApplicable) {
            LOGGER.log(LogLevelUtil.CONSOLE_SUCCESS, "{} Multibit injection", () -> I18nUtil.valueByKey(KEY_LOG_VULNERABLE));
            this.allow();

            var requestMessageBinary = new Request();
            requestMessageBinary.setMessage(Interaction.MESSAGE_BINARY);
            requestMessageBinary.setParameters(this.injectionMultibit.getInfoMessage());
            this.injectionModel.sendToViews(requestMessageBinary);
        } else {
            this.unallow();
        }
    }

    @Override
    public void allow(int... i) {
        this.injectionModel.appendAnalysisReport(
            StringUtil.formatReport(LogLevelUtil.COLOR_BLU, "### Strategy: " + getName())
            + this.injectionModel.getReportWithoutIndex(
                injectionModel.getMediatorVendor().getVendor().instance().sqlMultibit(
                    this.injectionModel.getMediatorVendor().getVendor().instance().sqlBlind(
                        StringUtil.formatReport(LogLevelUtil.COLOR_GREEN, "&lt;query&gt;"),
                        "0",
                        true
                    ),
                    0,
                    1
                ),
                "metadataInjectionProcess",
                null
            )
        );
        this.markVulnerability(Interaction.MARK_MULTI_VULNERABLE);
    }

    @Override
    public void unallow(int... i) {
        this.markVulnerability(Interaction.MARK_MULTI_INVULNERABLE);
    }

    @Override
    public String inject(String sqlQuery, String startPosition, AbstractSuspendable stoppable, String metadataInjectionProcess) throws StoppedByUserSlidingException {
        return this.injectionMultibit.inject(
            this.injectionModel.getMediatorVendor().getVendor().instance().sqlBlind(sqlQuery, startPosition, false),
            stoppable
        );
    }

    @Override
    public void activateWhenApplicable() {
        if (this.injectionModel.getMediatorStrategy().getStrategy() == null && this.isApplicable()) {
            LOGGER.log(
                LogLevelUtil.CONSOLE_INFORM,
                "{} [{}]",
                () -> I18nUtil.valueByKey("LOG_USING_STRATEGY"),
                this::getName
            );
            this.injectionModel.getMediatorStrategy().setStrategy(this.injectionModel.getMediatorStrategy().getMultibit());

            var requestMarkBlindStrategy = new Request();
            requestMarkBlindStrategy.setMessage(Interaction.MARK_MULTI_STRATEGY);
            this.injectionModel.sendToViews(requestMarkBlindStrategy);
        }
    }
    
    @Override
    public String getPerformanceLength() {
        return VendorYaml.DEFAULT_CAPACITY;
    }
    
    @Override
    public String getName() {
        return "Multibit";
    }
}
