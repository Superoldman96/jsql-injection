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
package com.jsql.view.swing.text;

import com.jsql.util.LogLevelUtil;
import com.jsql.view.swing.popupmenu.JPopupMenuText;
import com.jsql.view.swing.text.action.SilentDeleteTextAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.event.ActionEvent;

/**
 * A swing JTextComponent with Undo/Redo functionality.
 * @param <T> Component like JTextField or JTextArea to decorate
 */
public class JPopupTextComponent<T extends JTextComponent> extends JPopupComponent<T> implements DecoratorJComponent<T> {
    
    private static final Logger LOGGER = LogManager.getRootLogger();

    /**
     * Save the component to decorate, add the Undo/Redo.
     * @param proxy Swing component to decorate
     */
    public JPopupTextComponent(final T proxy) {
        super(proxy);

        this.getProxy().setComponentPopupMenu(new JPopupMenuText(this.getProxy()));
        this.getProxy().setDragEnabled(true);

        var undoRedoManager = new UndoManager();
        var doc = this.getProxy().getDocument();

        // Listen for undo and redo events
        doc.addUndoableEditListener(undoableEditEvent -> undoRedoManager.addEdit(undoableEditEvent.getEdit()));

        this.initUndo(undoRedoManager);
        this.initRedo(undoRedoManager);
        this.makeDeleteSilent();
    }

    private void initUndo(final UndoManager undo) {
        final var undoIdentifier = "Undo";  // Create an undo action and add it to the text component
        
        this.getProxy().getActionMap().put(undoIdentifier, new AbstractAction(undoIdentifier) {
            @Override
            public void actionPerformed(ActionEvent evt) {
                // Unhandled ArrayIndexOutOfBoundsException #92146 on undo()
                try {
                    if (undo.canUndo()) {
                        undo.undo();
                    }
                } catch (ArrayIndexOutOfBoundsException | CannotUndoException e) {
                    LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
                }
            }
       });

        // Bind the undo action to ctl-Z
        this.getProxy().getInputMap().put(KeyStroke.getKeyStroke("control Z"), undoIdentifier);
    }

    private void initRedo(final UndoManager undo) {
        final var redoIdentifier = "Redo";  // Create a redo action and add it to the text component
        
        this.getProxy().getActionMap().put(redoIdentifier, new AbstractAction(redoIdentifier) {
            @Override
            public void actionPerformed(ActionEvent evt) {
                try {
                    if (undo.canRedo()) {
                        undo.redo();
                    }
                } catch (CannotRedoException e) {
                    LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
                }
            }
        });

        // Bind the redo action to ctl-Y
        this.getProxy().getInputMap().put(KeyStroke.getKeyStroke("control Y"), redoIdentifier);
    }

    private void makeDeleteSilent() {
        var actionMap = this.getProxy().getActionMap();  // Silent delete

        String key = DefaultEditorKit.deletePrevCharAction;
        actionMap.put(key, new SilentDeleteTextAction(key, actionMap.get(key)));

        key = DefaultEditorKit.deleteNextCharAction;
        actionMap.put(key, new SilentDeleteTextAction(key, actionMap.get(key)));
    }
}
