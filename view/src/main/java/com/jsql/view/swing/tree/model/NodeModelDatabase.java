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
package com.jsql.view.swing.tree.model;

import com.jsql.model.bean.database.Database;
import com.jsql.util.LogLevelUtil;
import com.jsql.view.swing.tree.custom.JPopupMenuCustomExtract;
import com.jsql.view.swing.util.MediatorHelper;
import com.jsql.view.swing.util.UiUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Database model displaying the database icon on the label.
 */
public class NodeModelDatabase extends AbstractNodeModel {
    
    private static final Logger LOGGER = LogManager.getRootLogger();
    
    /**
     * Node as a database model.
     * @param database Element database coming from model
     */
    public NodeModelDatabase(Database database) {
        super(database);
    }

    @Override
    protected Icon getLeafIcon(boolean leaf) {
        if (leaf) {
            return UiUtil.DATABASE_LINEAR.getIcon();
        } else {
            return UiUtil.DATABASE_BOLD.getIcon();
        }
    }

    @Override
    public void runAction() {
        if (this.isRunning()) {
            return;
        }
    
        MediatorHelper.treeDatabase().getTreeNodeModels().get(this.getElementDatabase()).removeAllChildren();
        DefaultTreeModel treeModel = (DefaultTreeModel) MediatorHelper.treeDatabase().getModel();
        // Fix #90522: ArrayIndexOutOfBoundsException on reload()
        try {
            treeModel.reload(MediatorHelper.treeDatabase().getTreeNodeModels().get(this.getElementDatabase()));
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
        }
        
        new SwingWorker<>() {
            @Override
            protected Object doInBackground() throws Exception {
                Thread.currentThread().setName("SwingWorkerNodeModelDatabase");
                var selectedDatabase = (Database) NodeModelDatabase.this.getElementDatabase();
                return MediatorHelper.model().getDataAccess().listTables(selectedDatabase);
            }
        }.execute();
        
        this.setRunning(true);
    }

    @Override
    public boolean isPopupDisplayable() {
        return this.isLoaded() || !this.isLoaded() && this.isRunning();
    }

    @Override
    protected void buildMenu(JPopupMenuCustomExtract tablePopupMenu, TreePath path) {
        // Do nothing
    }
}
