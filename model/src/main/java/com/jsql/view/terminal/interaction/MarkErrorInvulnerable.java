/*******************************************************************************
 * Copyhacked (H) 2012-2020.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss about it
 * every time possible with every body.
 * 
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 ******************************************************************************/
package com.jsql.view.terminal.interaction;

import com.jsql.model.InjectionModel;
import com.jsql.model.bean.util.Header;
import com.jsql.util.AnsiColorUtil;
import com.jsql.view.interaction.InteractionCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Mark the injection as invulnerable to a error based injection.
 */
public class MarkErrorInvulnerable implements InteractionCommand {
    
    private static final Logger LOGGER = LogManager.getRootLogger();
    
    private final int indexMethodError;
    private final InjectionModel injectionModel;
    
    @SuppressWarnings("unchecked")
    public MarkErrorInvulnerable(Object[] interactionParams) {
        
        Map<Header, Object> mapHeader = (Map<Header, Object>) interactionParams[0];
        this.indexMethodError = (int) mapHeader.get(Header.INDEX_ERROR_STRATEGY);
        this.injectionModel = (InjectionModel) mapHeader.get(Header.INJECTION_MODEL);
    }

    @Override
    public void execute() {
        LOGGER.debug(
            () -> AnsiColorUtil.addRedColor(
                this.injectionModel
                .getMediatorVendor()
                .getVendor()
                .instance()
                .getModelYaml()
                .getStrategy()
                .getError()
                .getMethod()
                .get(this.indexMethodError)
                .getName()
            )
        );
    }
}
