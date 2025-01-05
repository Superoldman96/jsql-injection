/*******************************************************************************
 * Copyhacked (H) 2012-2025.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss it
 * every time possible with every body.
 * 
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 ******************************************************************************/
package com.jsql.view.swing.shell;

import com.jsql.view.swing.util.MediatorHelper;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.UUID;

/**
 * A terminal for SQL shell injection.
 */
public class ShellSql extends AbstractShell {
    
    /**
     * Build a SQL shell instance.
     * @param terminalID Unique identifier to discriminate beyond multiple opened terminals
     * @param urlShell URL of current shell
     * @param loginPassword User and password
     */
    public ShellSql(UUID terminalID, String urlShell, String... loginPassword) throws MalformedURLException, URISyntaxException {
        super(terminalID, urlShell, "sql");
        this.loginPassword = loginPassword;
    }

    @Override
    public void action(String cmd, UUID terminalID, String wbhPath, String... arg) {
        MediatorHelper.model().getResourceAccess().runSqlShell(cmd, terminalID, wbhPath, arg[0], arg[1]);
    }
}
